#!/usr/bin/env python3
from pathlib import Path
import subprocess, secrets, os

root = Path(__file__).resolve().parents[1]
p = root / ".env"
assert not p.exists(), "Refuse to replace existing .env"
r = subprocess.run(
    [
        "mysql",
        "-u",
        "root",
        "-N",
        "-e",
        "SELECT COUNT(*) FROM mysql.user WHERE User='scm_workbench'; SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='scm_workbench';",
    ],
    check=True,
    capture_output=True,
    text=True,
)
assert r.stdout.split() == ["0", "0"], "Existing project DB or user needs review"
pw = secrets.token_hex(24)
sql = f"CREATE DATABASE scm_workbench CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; CREATE USER 'scm_workbench'@'127.0.0.1' IDENTIFIED BY '{pw}'; GRANT SELECT,INSERT,UPDATE,DELETE,CREATE,ALTER,INDEX,REFERENCES ON scm_workbench.* TO 'scm_workbench'@'127.0.0.1';"
subprocess.run(
    ["mysql", "-u", "root"], input=sql, text=True, check=True, capture_output=True
)
fd = os.open(p, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
with os.fdopen(fd, "w") as f:
    f.write(
        "DB_HOST=127.0.0.1\nDB_PORT=3306\nDB_NAME=scm_workbench\nDB_USERNAME=scm_workbench\nDB_PASSWORD="
        + pw
        + "\nMOCK_PORT=4010\nBACKEND_PORT=8080\nFRONTEND_PORT=5173\n"
    )
print("Dedicated DB and account created; .env mode 0600")
