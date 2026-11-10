"""Preparação de dados públicos OSM; nunca lê banco, senhas ou carteira."""
import collections
import datetime
import hashlib
import json
import os
import pathlib
import urllib.request
import uuid

ROOT = pathlib.Path(__file__).resolve().parents[1]
CONFIG = ROOT / "src/main/resources/catalog-regions.json"


def configuration():
    return json.loads(CONFIG.read_text(encoding="utf-8"))


def selected_regions(ids=None):
    regions = configuration()["regions"]
    requested = set(ids or [region["id"] for region in regions])
    if requested - {region["id"] for region in regions}:
        raise ValueError("Região não configurada.")
    return [region for region in regions if region["id"] in requested]


def resolve_catalog(output, verify=False):
    output = pathlib.Path(output)
    manifest = output.with_name(output.name + ".snapshot.json")
    if not manifest.exists():
        return output
    pointer = json.loads(manifest.read_text(encoding="utf-8"))
    required = {"path", "version", "dataSha256", "coverageSha256", "metaSha256"}
    if not isinstance(pointer, dict) or not required.issubset(pointer) or not isinstance(pointer["path"], str):
        raise ValueError("Manifesto de catálogo inválido.")
    active = (output.parent / pointer["path"]).resolve()
    allowed = (output.parent / "catalogos/snapshots").resolve()
    if not active.is_relative_to(allowed) or active.name != output.name:
        raise ValueError("Ponteiro de catálogo inválido.")
    for suffix, key in (("", "dataSha256"), (".coverage.poly", "coverageSha256"), (".meta.json", "metaSha256")):
        file = active.with_name(active.name + suffix)
        if not file.is_file():
            raise ValueError("Snapshot de catálogo incompleto.")
        if verify:
            digest = hashlib.sha256()
            with file.open("rb") as source:
                while block := source.read(1024 * 1024):
                    digest.update(block)
            if digest.hexdigest() != pointer[key]:
                raise ValueError("Snapshot de catálogo não passou pela verificação de integridade.")
    return active


def download(region, refresh=False):
    folder = ROOT / ".tools/osm"
    folder.mkdir(parents=True, exist_ok=True)
    target = folder / (region["id"] + ".osm.pbf")
    if refresh or not target.exists() or target.stat().st_size < 1_000_000:
        print("Baixando " + region["name"] + "...", flush=True)
        req = urllib.request.Request(region["pbf"], headers={"User-Agent": "Prospectgt/0.2 regional catalog setup"})
        partial = target.with_suffix(".partial")
        with urllib.request.urlopen(req, timeout=90) as response:
            expected = int(response.headers.get("Content-Length", 0))
            digest = hashlib.sha256()
            count = 0
            next_progress = 50 * 1024 * 1024
            with partial.open("wb") as output:
                while block := response.read(1024 * 1024):
                    output.write(block)
                    digest.update(block)
                    count += len(block)
                    if count >= next_progress:
                        print(region["name"] + ": " + str(count // 1024 // 1024) + " MB", flush=True)
                        next_progress += 50 * 1024 * 1024
            if (expected and count != expected) or count < 1_000_000:
                raise RuntimeError("Download incompleto: " + region["name"])
            os.replace(partial, target)
            target.with_suffix(".meta.json").write_text(json.dumps({"url": region["pbf"], "sha256": digest.hexdigest(), "bytes": count, "lastModified": response.headers.get("Last-Modified")}), encoding="utf-8")
    polygon = folder / (region["id"] + ".poly")
    if refresh or not polygon.exists():
        with urllib.request.urlopen(region["polygon"], timeout=30) as response:
            content = response.read(1024 * 1024).decode("utf-8")
        if not content.strip().endswith("END"):
            raise RuntimeError("Polígono de cobertura incompleto.")
        temporary = polygon.with_suffix(".partial")
        temporary.write_text(content, encoding="utf-8")
        os.replace(temporary, polygon)
    print(region["name"] + ": recorte disponível.", flush=True)
    return target, polygon


def combine_catalogs(catalogs, output):
    """Une por chave OSM e publica apenas depois de validar todos os arquivos."""
    output = pathlib.Path(output)
    output.parent.mkdir(parents=True, exist_ok=True)
    # Pré-validar coberturas: não ampliar um polígono sem seu catálogo correspondente.
    boundaries = []
    for region, catalog, polygon in catalogs:
        if not pathlib.Path(catalog).is_file():
            raise FileNotFoundError(catalog)
        lines = pathlib.Path(polygon).read_text(encoding="utf-8").strip().splitlines()
        if len(lines) < 5 or lines[-1].strip() != "END":
            raise ValueError("Polígono inválido: " + region["id"])
        # Regiões Geofabrik configuradas são recortes contíguos, sem sobreposição interna.
        boundaries.extend(lines[1:-1])
    # Cada versão é imutável. Só o ponteiro é ativado atomicamente ao fim.
    version = uuid.uuid4().hex
    snapshot = output.parent / "catalogos/snapshots" / version
    snapshot.mkdir(parents=True)
    active = snapshot / output.name
    temporary = active.with_suffix(active.suffix + ".partial")
    seen = set()
    categories = collections.Counter()
    contacts = 0
    with temporary.open("w", encoding="utf-8", newline="\n") as combined:
        for region, catalog, polygon in catalogs:
            with pathlib.Path(catalog).open(encoding="utf-8") as source:
                for line in source:
                    if not line.strip():
                        continue
                    place = json.loads(line)
                    if place["key"] in seen:
                        continue
                    seen.add(place["key"])
                    categories[place["category"]] += 1
                    contacts += bool(place.get("phone") or place.get("whatsapp"))
                    combined.write(json.dumps(place, ensure_ascii=False, separators=(",", ":")) + "\n")
    meta = {"region": " + ".join(region["name"] for region, _, _ in catalogs), "regions": [region["id"] for region, _, _ in catalogs], "preparedAt": datetime.datetime.now(datetime.timezone.utc).isoformat(), "records": len(seen), "withContact": contacts, "categories": dict(sorted(categories.items())), "license": "ODbL 1.0 — © OpenStreetMap contributors", "sources": [region["page"] for region, _, _ in catalogs]}
    polygon_output = active.with_name(active.name + ".coverage.poly")
    polygon_temp = polygon_output.with_suffix(".partial")
    polygon_temp.write_text("Prospectgt\n" + "\n".join(boundaries) + "\nEND\n", encoding="utf-8")
    meta_output = active.with_name(active.name + ".meta.json")
    meta_temp = meta_output.with_suffix(".partial")
    meta_temp.write_text(json.dumps(meta, ensure_ascii=False, indent=2), encoding="utf-8")
    os.replace(temporary, active)
    os.replace(polygon_temp, polygon_output)
    os.replace(meta_temp, meta_output)
    pointer = {"path": active.relative_to(output.parent).as_posix(), "version": version}
    for file, key in ((active, "dataSha256"), (polygon_output, "coverageSha256"), (meta_output, "metaSha256")):
        digest = hashlib.sha256()
        with file.open("rb") as source:
            while block := source.read(1024 * 1024):
                digest.update(block)
        pointer[key] = digest.hexdigest()
    manifest = output.with_name(output.name + ".snapshot.json")
    pointer_temp = manifest.with_name(manifest.name + "." + version + ".partial")
    pointer_temp.write_text(json.dumps(pointer, indent=2), encoding="utf-8")
    os.replace(pointer_temp, manifest)
    return meta
