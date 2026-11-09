"""Prepara e executa o MVP local. Uso: python scripts/run.py [--prepare-only]."""
import pathlib, subprocess, sys

root = pathlib.Path(__file__).resolve().parents[1]


def run(script, *args):
    subprocess.run([sys.executable, str(root / "scripts" / script), *args], cwd=root, check=True)


if not (root / ".tools" / "toolchain.json").exists():
    run("bootstrap.py")
run("maven.py", "-q", "compile", "-DskipTests", "dependency:build-classpath", "-Dmdep.outputFile=.tools/classpath.txt")
if not (root / "data" / "catalogo-osm.jsonl").exists() or not (root / "data" / "catalogo-osm.jsonl.coverage.poly").exists():
    run("import-catalog.py")
if "--prepare-only" not in sys.argv:
    print("\nProspectgt: http://127.0.0.1:8080\nAcessos individuais: data/acessos-iniciais.txt\nCtrl+C encerra o servidor.\n", flush=True)
    try:
        run("maven.py", "-q", "spring-boot:run")
    except KeyboardInterrupt:
        pass
