"""Empacota somente o snapshot público num único ZIP substituído atomicamente."""
import json
import os
import zipfile
import uuid
from catalog_data import ROOT, resolve_catalog

source = resolve_catalog(ROOT / "data/catalogo-osm.jsonl", verify=True)
meta = json.loads(source.with_name(source.name + ".meta.json").read_text(encoding="utf-8"))
if not {"centro-oeste", "sudeste"}.issubset(set(meta.get("regions", []))):
    raise RuntimeError("Prepare Centro-Oeste + Sudeste antes de publicar.")
destination = ROOT / "deploy"
destination.mkdir(parents=True, exist_ok=True)
target = destination / "catalog.zip"
temporary = destination / ("catalog." + uuid.uuid4().hex + ".partial.zip")
records = 0
with zipfile.ZipFile(temporary, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=6) as package:
    with package.open(source.name, "w") as compressed, source.open(encoding="utf-8") as data:
        for line in data:
            entry = json.loads(line)
            if not str(entry.get("key", "")).startswith("osm:") or not str(entry.get("sourceUrl", "")).startswith("https://www.openstreetmap.org/"):
                raise RuntimeError("O arquivo contém dados fora do catálogo público OSM.")
            compressed.write(line.encode("utf-8"))
            records += 1
    if records != meta["records"]:
        raise RuntimeError("Contagem difere dos metadados.")
    for suffix in (".coverage.poly", ".meta.json"):
        file = source.with_name(source.name + suffix)
        package.write(file, file.name)
os.replace(temporary, target)
print(json.dumps({"records": records, "compressedBytes": target.stat().st_size, "output": str(target)}))
