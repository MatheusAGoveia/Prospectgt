"""Instala JDK/Maven portateis no projeto; verifica hashes dos distribuidores."""
import concurrent.futures, hashlib, json, pathlib, urllib.request, zipfile
ROOT = pathlib.Path(__file__).resolve().parents[1]
TOOLS = ROOT / ".tools"
TOOLS.mkdir(exist_ok=True)
def fetch(url):
    return urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "Prospectgt-bootstrap/0.1"}), timeout=60)
def install(name, url, expected, algo):
    archive = TOOLS / (name + ".zip")
    destination = TOOLS / name
    if destination.exists() and any(destination.iterdir()):
        return str(next(p for p in destination.iterdir() if p.is_dir()).resolve())
    print("Baixando " + name, flush=True)
    digest = hashlib.new(algo)
    with fetch(url) as response, archive.open("wb") as out:
        while chunk := response.read(1024 * 1024):
            digest.update(chunk); out.write(chunk)
    if digest.hexdigest().lower() != expected.lower():
        raise RuntimeError("Hash incorreto: " + name)
    destination.mkdir(exist_ok=True)
    with zipfile.ZipFile(archive) as z:
        for member in z.namelist():
            resolved = (destination / member).resolve()
            if not resolved.is_relative_to(destination.resolve()):
                raise RuntimeError("Caminho de arquivo invalido")
        z.extractall(destination)
    return str(next(p for p in destination.iterdir() if p.is_dir()).resolve())
jdk = json.load(fetch("https://api.adoptium.net/v3/assets/latest/21/hotspot?architecture=x64&image_type=jdk&os=windows"))[0]["binary"]["package"]
version = "3.9.16"
maven_url = "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/" + version + "/apache-maven-" + version + "-bin.zip"
checksum = fetch(maven_url + ".sha512").read().decode().split()[0]
with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
    j = pool.submit(install, "jdk", jdk["link"], jdk["checksum"], "sha256")
    m = pool.submit(install, "maven", maven_url, checksum, "sha512")
    configuration = {"java": j.result(), "maven": m.result()}
(TOOLS / "toolchain.json").write_text(json.dumps(configuration, indent=2), encoding="utf-8")
print("Toolchain portatil pronta.", flush=True)
