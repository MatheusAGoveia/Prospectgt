"""Baixa o recorte regional real utilizado pelo piloto, sem APIs pagas."""
import pathlib,urllib.request,json,hashlib
root=pathlib.Path(__file__).resolve().parents[1]
folder=root/".tools"/"osm";folder.mkdir(parents=True,exist_ok=True)
target=folder/"centro-oeste.osm.pbf"
url="https://download.geofabrik.de/south-america/brazil/centro-oeste-latest.osm.pbf"
if target.exists() and target.stat().st_size>1000000:
 print("Recorte ja disponivel.",flush=True)
else:
 print("Baixando recorte OSM Centro-Oeste...",flush=True)
 req=urllib.request.Request(url,headers={"User-Agent":"Prospectgt/0.1 local MVP setup"})
 with urllib.request.urlopen(req,timeout=90) as response:
  expected=int(response.headers.get("Content-Length",0));n=0;digest=hashlib.sha256()
  with target.with_suffix(".partial").open("wb") as f:
   while chunk:=response.read(1024*1024):
    f.write(chunk);digest.update(chunk);n+=len(chunk)
    if n%(25*1024*1024)<1024*1024:print(str(n//1024//1024)+" MB",flush=True)
  if expected and n!=expected:raise RuntimeError("Download incompleto")
  target.with_suffix(".partial").replace(target)
  target.with_suffix(".meta.json").write_text(json.dumps({"url":url,"sha256":digest.hexdigest(),"bytes":n,"lastModified":response.headers.get("Last-Modified")}),encoding="utf-8")
 print("Recorte baixado e tamanho validado.",flush=True)

coverage=root/"data"/"catalogo-osm.jsonl.coverage.poly"
coverage.parent.mkdir(parents=True,exist_ok=True)
polygon_url="https://download.geofabrik.de/south-america/brazil/centro-oeste.poly"
if not coverage.exists():
 with urllib.request.urlopen(polygon_url,timeout=30) as response:
  polygon=response.read(1024*1024).decode("utf-8")
  if not polygon.strip().endswith("END"):raise RuntimeError("Poligono de cobertura incompleto")
  coverage.write_text(polygon,encoding="utf-8")
 print("Limite geografico do catalogo preparado.",flush=True)
