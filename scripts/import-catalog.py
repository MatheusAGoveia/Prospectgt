import pathlib,json,os,subprocess,sys
root=pathlib.Path(__file__).resolve().parents[1]
source=root/".tools"/"osm"/"centro-oeste.osm.pbf"
subprocess.run([sys.executable,str(root/"scripts"/"download-data.py")],cwd=root,check=True)
tools=json.loads((root/".tools"/"toolchain.json").read_text())
cp=str(root/"target"/"classes")+os.pathsep+(root/".tools"/"classpath.txt").read_text().strip()
subprocess.run([str(pathlib.Path(tools["java"])/"bin"/"java.exe"),"-Xmx1200m","-cp",cp,"br.com.wezttech.prospectgt.descoberta.PbfCatalogImporter",str(source),str(root/"data"/"catalogo-osm.jsonl"),"Centro-Oeste"],cwd=root,check=True)
