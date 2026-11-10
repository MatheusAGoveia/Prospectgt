import argparse, json, os, pathlib, subprocess
from catalog_data import ROOT, combine_catalogs, download, selected_regions

parser = argparse.ArgumentParser()
parser.add_argument("--refresh", action="store_true")
options = parser.parse_args()
tools = json.loads((ROOT / ".tools/toolchain.json").read_text())
java = pathlib.Path(tools["java"]) / "bin" / ("java.exe" if os.name == "nt" else "java")
cp = str(ROOT / "target/classes") + os.pathsep + (ROOT / ".tools/classpath.txt").read_text().strip()
catalogs = []
for region in selected_regions():
    source, polygon = download(region, options.refresh)
    output = ROOT / "data/catalogos" / (region["id"] + ".jsonl")
    if options.refresh or not output.exists() or output.stat().st_mtime < source.stat().st_mtime:
        subprocess.run([str(java), "-Xmx1200m", "-cp", cp, "br.com.wezttech.prospectgt.descoberta.PbfCatalogImporter", str(source), str(output), region["name"], region["page"]], cwd=ROOT, check=True)
    catalogs.append((region, output, polygon))
print(json.dumps(combine_catalogs(catalogs, ROOT / "data/catalogo-osm.jsonl"), ensure_ascii=False, indent=2))
