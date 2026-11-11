import pathlib,urllib.request
root=pathlib.Path(__file__).resolve().parents[1]/"src"/"main"/"resources"/"static"/"assets"/"vendor"/"leaflet"
for name in ["leaflet.js","leaflet.css","images/layers.png","images/layers-2x.png","images/marker-icon.png","images/marker-icon-2x.png","images/marker-shadow.png"]:
 target=root/name;target.parent.mkdir(parents=True,exist_ok=True)
 with urllib.request.urlopen("https://unpkg.com/leaflet@1.9.4/dist/"+name,timeout=30) as r:target.write_bytes(r.read())
with urllib.request.urlopen("https://raw.githubusercontent.com/Leaflet/Leaflet/v1.9.4/LICENSE",timeout=30) as r:(root/"LICENSE").write_bytes(r.read())
print("Leaflet 1.9.4 instalado localmente com licença.")
fonts=root.parent/"poppins"
fonts.mkdir(parents=True,exist_ok=True)
base="https://cdn.jsdelivr.net/npm/@fontsource/poppins@5.0.14/"
for w in [400,500,600,700,800]:
 with urllib.request.urlopen(base+f"files/poppins-latin-{w}-normal.woff2",timeout=30) as r:(fonts/f"poppins-{w}.woff2").write_bytes(r.read())
with urllib.request.urlopen(base+"LICENSE",timeout=30) as r:(fonts/"LICENSE").write_bytes(r.read())
print("Poppins instalada localmente com licença OFL.")
