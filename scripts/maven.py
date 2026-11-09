import json,pathlib,subprocess,os,sys
root=pathlib.Path(__file__).resolve().parents[1]
config=root/".tools"/"toolchain.json"
if not config.exists():subprocess.run([sys.executable,str(root/"scripts"/"bootstrap.py")],check=True)
tools=json.loads(config.read_text(encoding="utf-8"))
env=os.environ.copy();env["JAVA_HOME"]=tools["java"]
if os.name=="nt":env["JAVA_TOOL_OPTIONS"]=env.get("JAVA_TOOL_OPTIONS","")+" -Djavax.net.ssl.trustStoreType=Windows-ROOT -Djavax.net.ssl.trustStore=NONE"
command=str(pathlib.Path(tools["maven"])/"bin"/("mvn.cmd" if os.name=="nt" else "mvn"))
sys.exit(subprocess.call([command,*sys.argv[1:]],cwd=root,env=env))
