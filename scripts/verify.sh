#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
python3 - <<'PY'
import os,subprocess,json,urllib.request,urllib.error
from pathlib import Path
for line in Path('.env').read_text().splitlines():
    if line and not line.startswith('#'):
        k,v=line.split('=',1);os.environ.setdefault(k,v)
subprocess.run(['python3','scripts/test-lifecycle.py'],check=True)
subprocess.run(['mvn','-B','-f','backend/pom.xml','verify'],env={**os.environ,'SCM_DB_TEST':'true'},check=True)
subprocess.run(['npm','test'],cwd='frontend',check=True)
subprocess.run(['npm','run','typecheck'],cwd='frontend',check=True)
subprocess.run(['npm','run','build'],cwd='frontend',check=True)
subprocess.run(['npm','test'],cwd='mock-service',check=True)
subprocess.run(['python3','scripts/dev.py','init'],check=True)
subprocess.run(['python3','scripts/dev.py','init'],check=True)
b='http://127.0.0.1:'+os.getenv('BACKEND_PORT','8080')
f='http://127.0.0.1:'+os.getenv('FRONTEND_PORT','5173')
m='http://127.0.0.1:'+os.getenv('MOCK_PORT','4010')
def get(url):
    with urllib.request.urlopen(url,timeout=10) as r: return json.load(r)
assert get(b+'/actuator/health')['status']=='UP'
assert get(f+'/api/workbench/status')['data']['database']=='UP'
assert get(f+'/api/data-sources/mock/preview')['data']['data']==get(m+'/api/procurement')['data']
assert '/api/workbench/status' in get(b+'/v3/api-docs')['paths']
try: get(b+'/api/data-sources/mock/preview?type=invalid')
except urllib.error.HTTPError as e: assert e.code==400
else: raise AssertionError('Validation failed')
print('PASS: real MySQL, health, frontend proxy, HTTP adapter, OpenAPI, validation, idempotent SQL')
PY
