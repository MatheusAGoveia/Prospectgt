"""Migra H2 local para um PostgreSQL vazio. Conexão recebida sem eco e sem arquivo."""
import getpass, json, os, pathlib, re, subprocess, sys
from urllib.parse import urlsplit, unquote, urlencode

root=pathlib.Path(__file__).resolve().parents[1]
print("Migração pronta. A conexão de destino será recebida em entrada oculta.",flush=True)
if "--clipboard" in sys.argv:
    # Lê a conexão copiada no Neon sem exibi-la nem gravá-la em disco.
    raw=subprocess.run(["powershell","-NoProfile","-Command","Get-Clipboard -Raw"],capture_output=True,text=True).stdout.strip()
    print("Conexão lida da área de transferência.",flush=True)
else:
    raw=getpass.getpass("Cole a connection string do Neon (oculta): ").strip()
# Aceita a URL pura, o comando psql '...' do Neon ou o JSON antigo.
if re.fullmatch(r"[^\s:@/'\"]+:[^\s@'\"]+@[^\s/'\"]+/[^\s'\"]+",raw): raw="postgresql://"+raw  # copiado sem o prefixo
found=re.search(r"postgres(?:ql)?://[^\s'\"]+",raw)
if found: value={"connectionString":found.group(0)}
else:
    try: value=json.loads(raw)
    except ValueError: raise SystemExit("Não encontrei uma connection string postgresql:// no texto colado. Nada foi alterado.")
if "connectionString" in value:
    url=urlsplit(value["connectionString"])
    if url.scheme not in ("postgres","postgresql") or not url.hostname or not url.username or not url.password:
        raise ValueError("Conexão PostgreSQL inválida.")
    # JSSE valida o certificado e o hostname usando a cadeia de confiança padrão.
    connection={"url":"jdbc:postgresql://"+url.hostname+":"+str(url.port or 5432)+url.path+"?"+urlencode({"sslmode":"verify-full","sslfactory":"org.postgresql.ssl.DefaultJavaSSLFactory","connectTimeout":15}),"user":unquote(url.username),"password":unquote(url.password)}
else:
    connection=value
config=json.loads((root/".tools/toolchain.json").read_text())
env=os.environ.copy()
env.update({"MIGRATION_DB_URL":connection["url"],"MIGRATION_DB_USER":connection["user"],"MIGRATION_DB_PASSWORD":connection["password"]})
if os.name=="nt":
    env["JAVA_TOOL_OPTIONS"]=env.get("JAVA_TOOL_OPTIONS","")+" -Djavax.net.ssl.trustStoreType=Windows-ROOT -Djavax.net.ssl.trustStore=NONE"
cp=str(root/"target/classes")+os.pathsep+(root/".tools/classpath.txt").read_text().strip()
java=pathlib.Path(config["java"])/"bin"/("java.exe" if os.name=="nt" else "java")
result=subprocess.run([str(java),"-cp",cp,"br.com.wezttech.prospectgt.operacao.MigrarBancoLocal"],cwd=root,env=env)
raise SystemExit(result.returncode)
