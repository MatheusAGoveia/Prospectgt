"""Prepara e executa o MVP local. Uso: python scripts/run.py [--prepare-only]."""
import json, pathlib, subprocess, sys
from catalog_data import configuration, resolve_catalog

root = pathlib.Path(__file__).resolve().parents[1]


def run(script, *args):
    subprocess.run([sys.executable, str(root / "scripts" / script), *args], cwd=root, check=True)


if not (root / ".tools" / "toolchain.json").exists():
    run("bootstrap.py")
run("maven.py", "-q", "compile", "-DskipTests", "dependency:build-classpath", "-Dmdep.outputFile=.tools/classpath.txt")
try:
    active_catalog = resolve_catalog(root / "data/catalogo-osm.jsonl", verify=True)
    meta_path = active_catalog.with_name(active_catalog.name + ".meta.json")
    prepared_regions = set(json.loads(meta_path.read_text(encoding="utf-8")).get("regions", [])) if meta_path.exists() else set()
except (ValueError, OSError):
    active_catalog = root / "data/catalogo-osm.jsonl"
    prepared_regions = set()
required_regions = {region["id"] for region in configuration()["regions"]}
if not active_catalog.exists() or not active_catalog.with_name(active_catalog.name + ".coverage.poly").exists() or not required_regions.issubset(prepared_regions):
    run("import-catalog.py")
if "--prepare-only" not in sys.argv:
    print("\nProspectgt: http://127.0.0.1:8080\nAcessos individuais: data/acessos-iniciais.txt\nCtrl+C encerra o servidor.\n", flush=True)
    try:
        run("maven.py", "-q", "spring-boot:run")
    except KeyboardInterrupt:
        pass
