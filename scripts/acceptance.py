#!/usr/bin/env python3
"""Exercise actual HTTP + CSV/XLSX ingestion; repeatable, only the project database."""
import json, os, subprocess, urllib.request, urllib.error, urllib.parse, uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
os.chdir(ROOT)
settings = {
    k: v
    for l in (ROOT / ".env").read_text().splitlines()
    if "=" in l and not l.startswith("#")
    for k, v in [l.split("=", 1)]
}
settings.update(os.environ)
base = "http://127.0.0.1:" + settings.get("BACKEND_PORT", "8080") + "/api"
port = settings.get("MOCK_PORT", "4010")


def api(path, method="GET", body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(
        base + path,
        data=data,
        method=method,
        headers={"Content-Type": "application/json"},
    )
    try:
        with urllib.request.urlopen(req, timeout=35) as r:
            return json.load(r)["data"]
    except urllib.error.HTTPError as e:
        detail = json.load(e)
        raise AssertionError(
            f"{method} {path}: HTTP {e.code} " + detail.get("message", "")
        ) from None


def upload(source, path):
    boundary = "scm-" + uuid.uuid4().hex
    body = (
        (
            f'--{boundary}\r\nContent-Disposition: form-data; name="sourceId"\r\n\r\n{source}\r\n--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{path.name}"\r\nContent-Type: application/octet-stream\r\n\r\n'
        ).encode()
        + path.read_bytes()
        + f"\r\n--{boundary}--\r\n".encode()
    )
    req = urllib.request.Request(
        base + "/imports",
        data=body,
        headers={"Content-Type": "multipart/form-data; boundary=" + boundary},
    )
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.load(r)["data"]


sources = api("/data-sources")
http = next(s for s in sources if s["name"] == "本地采购协同 Mock")
file = next(s for s in sources if s["name"] == "本地库存文件")
http.update(
    endpoint=f"http://127.0.0.1:{port}/api/acceptance",
    mappingConfig={"sourceRecordId": "id"},
    enabled=False,
    requestMethod="GET",
)
http = api("/data-sources/" + str(http["id"]), "PUT", http)
first = api(f"/data-sources/{http['id']}/sync", "POST")
assert (
    first["success_count"] + first["duplicate_count"] == 60
    and first["failure_count"] == 0
)
file.update(mappingConfig={}, enabled=False)
file = api("/data-sources/" + str(file["id"]), "PUT", file)
r = upload(file["id"], ROOT / "test-data/acceptance.csv")
assert r["success_count"] + r["duplicate_count"] == 40 and r["failure_count"] == 3
assert [e["line"] for e in r["errors"]] == [42, 43, 44]
repeat = upload(file["id"], ROOT / "test-data/acceptance.csv")
assert (
    repeat["success_count"] == 0
    and repeat["duplicate_count"] == 40
    and repeat["failure_count"] == 3
)
assert api(f"/data-sources/{http['id']}/sync", "POST")["duplicate_count"] == 60
# Configured POST uses the same JSON protocol and remains idempotent.
post_source = {**http, "requestMethod": "POST"}
api("/data-sources/" + str(http["id"]), "PUT", post_source)
assert api(f"/data-sources/{http['id']}/sync", "POST")["duplicate_count"] == 60
api("/data-sources/" + str(http["id"]), "PUT", http)
expected = json.loads((ROOT / "test-data/acceptance-expected.json").read_text())
summary = api("/metrics/summary")
for key, value in expected.items():
    if key.isupper():
        assert summary[key] == value, (key, summary[key], value)
all_rows = api("/todos?size=200")
assert (
    all_rows["now"] == "2026-11-17T12:00:00"
), "Acceptance requires SCM_FIXED_NOW=2026-11-17T04:00:00Z at startup"
assert all_rows["total"] == 100
for view in [
    "TOTAL",
    "TODAY_NEW",
    "TODAY_DUE",
    "OVERDUE",
    "DUE_SOON",
    "HIGH_RISK",
    "PENDING_REVIEW",
    "PENDING_REPLY",
    "WEEK_COMPLETED",
    "COMPLETED",
]:
    assert api("/todos?view=" + view)["total"] == summary[view], view
assert api("/todos?view=OVERDUE&businessType=PROCUREMENT&riskLevel=HIGH")["total"] == 3
assert api("/todos?view=MINE")["total"] == 40
charts = api("/metrics/charts?trendDays=7")
assert sum(charts["business"].values()) == 100 and sum(charts["risk"].values()) == 100
assert sum(x["count"] for x in charts["trend"]) == 100
for key, count in charts["business"].items():
    assert api("/todos?businessType=" + key)["total"] == count
for item in charts["trend"]:
    assert api("/todos?createdDate=" + item["date"])["total"] == item["count"]
for source in [http, file]:
    for page in ["rfq", "purchase", "stock", "supplier", "review"]:
        api(
            "/system-links",
            "POST",
            {
                "sourceId": source["id"],
                "pageCode": page,
                "urlTemplate": f"http://127.0.0.1:{port}/business/${{pageCode}}?no=${{businessNo}}&task=${{taskId}}&source=${{sourceSystem}}",
            },
        )
task = all_rows["items"][0]
link = api("/system-links/resolve?taskId=" + str(task["id"]))["url"]
assert "${" not in link and str(task["id"]) in link
with urllib.request.urlopen(link) as r:
    assert r.status == 200
config = api("/workbench/config")
try:
    changed = json.loads(json.dumps(config))
    changed["dueSoonHours"] = 48
    changed["cards"][0]["name"] = "验收持久化"
    changed["cards"][0]["order"] = 99
    api("/workbench/config", "PUT", changed)
    assert api("/workbench/config")["cards"][0]["name"] == "验收持久化"
    assert api("/metrics/summary")["DUE_SOON"] == 16
finally:
    api("/workbench/config", "PUT", config)
try:
    bad = {**http, "endpoint": f"http://127.0.0.1:{port}/api/failure"}
    api("/data-sources/" + str(http["id"]), "PUT", bad)
    try:
        api(f"/data-sources/{http['id']}/sync", "POST")
    except AssertionError as e:
        assert "HTTP 502" in str(e)
    else:
        raise AssertionError("Source failure not reported")
    assert (
        next(s for s in api("/data-sources") if s["id"] == http["id"])["syncStatus"]
        == "FAILED"
    )
    assert api("/todos")["total"] == 100
finally:
    api("/data-sources/" + str(http["id"]), "PUT", http)
    api(f"/data-sources/{http['id']}/sync", "POST")
xlsx = ROOT / "backend/target/test-fixtures/acceptance.xlsx"
assert (
    xlsx.exists()
), "Run real DB integration tests first to generate the Excel fixture"
if xlsx.exists():
    x = upload(file["id"], xlsx)
    assert x["duplicate_count"] == 1 and x["failure_count"] == 1
    upload(file["id"], ROOT / "test-data/acceptance.csv")
# Whole-file corruption is rejected and surfaced as a source failure, then recovered.
import tempfile

with tempfile.TemporaryDirectory() as directory:
    broken = Path(directory) / "broken.xlsx"
    broken.write_bytes(b"not-an-excel-file")
    try:
        upload(file["id"], broken)
    except urllib.error.HTTPError as e:
        assert e.code == 400
    else:
        raise AssertionError("Corrupt workbook was accepted")
assert (
    next(s for s in api("/data-sources") if s["id"] == file["id"])["syncStatus"]
    == "FAILED"
)
upload(file["id"], ROOT / "test-data/acceptance.csv")
# All business mutations must leave identifiable audit outcomes.
logs = api("/logs")
outcomes = {(r["operation_type"], r["result"]) for r in logs}
assert {
    ("CONFIG_UPDATE", "SUCCESS"),
    ("SOURCE_CONFIG", "SUCCESS"),
    ("LINK_CONFIG", "SUCCESS"),
    ("SYSTEM_JUMP", "RESOLVED"),
    ("HTTP_SYNC", "SUCCESS"),
    ("HTTP_SYNC", "FAILED"),
    ("FILE_IMPORT", "PARTIAL"),
    ("FILE_IMPORT", "FAILED"),
} <= outcomes, outcomes
assert all(r["created_at"] and r["target"] for r in logs)
# Independent database aggregates, not application metric code.
sql = """SELECT COUNT(*),COUNT(DISTINCT source_id),COUNT(DISTINCT business_type),SUM(status <> 'COMPLETED' AND due_at < '2026-11-17 12:00:00'),SUM(status <> 'COMPLETED' AND DATE(due_at)='2026-11-17'),SUM(status <> 'COMPLETED' AND risk_level='HIGH') FROM scm_todo; SELECT COUNT(*) FROM system_link_template;"""
env = {**os.environ, "MYSQL_PWD": settings["DB_PASSWORD"]}
result = subprocess.run(
    [
        "mysql",
        "--protocol=TCP",
        "-h",
        settings["DB_HOST"],
        "-P",
        settings["DB_PORT"],
        "-u",
        settings["DB_USERNAME"],
        settings["DB_NAME"],
        "-N",
    ],
    input=sql,
    text=True,
    env=env,
    capture_output=True,
    check=True,
)
lines = result.stdout.splitlines()
assert list(map(int, lines[0].split())) == [100, 2, 3, 20, 15, 8]
assert int(lines[1]) >= 5
report = {
    "result": "PASS",
    "summary": summary,
    "databaseCounts": lines,
    "checks": [
        "HTTP sync",
        "CSV partial import",
        "idempotence",
        "Excel mixed valid/formula rows",
        "10 metrics",
        "filters",
        "chart drill-down",
        "deep links",
        "config persistence",
        "operation audit outcomes",
        "source failure preserves stored rows",
    ],
}
(ROOT / ".runtime/acceptance-report.json").write_text(
    json.dumps(report, ensure_ascii=False, indent=2)
)
print(json.dumps(report, ensure_ascii=False, indent=2))
