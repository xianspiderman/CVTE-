#!/usr/bin/env python3
"""Exercise the configured company model against the isolated fixed acceptance data."""
import json
import os
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

root = Path(__file__).resolve().parents[1]
settings = dict(
    line.split("=", 1)
    for line in (root / ".env").read_text().splitlines()
    if "=" in line and not line.startswith("#")
)
settings.update(os.environ)
base = f"http://127.0.0.1:{settings.get('BACKEND_PORT', '8080')}/api"
mock_port = settings.get("MOCK_PORT", "4010")


def api(path, payload=None, method=None, status=200):
    data = None if payload is None else json.dumps(payload).encode()
    req = urllib.request.Request(
        base + path,
        data=data,
        method=method,
        headers={"Content-Type": "application/json"},
    )
    try:
        with urllib.request.urlopen(req, timeout=115) as res:
            assert res.status == status, (path, res.status)
            return json.load(res)["data"]
    except urllib.error.HTTPError as exc:
        detail = json.load(exc)
        assert exc.code == status, (path, exc.code, detail["message"])
        return detail


def ask(message, tool, session=None):
    reply = api("/ai/chat", {"sessionId": session, "message": message})
    evidence = next((e for e in reply["evidence"] if e["tool"] == tool), None)
    assert evidence is not None, (message, reply["tools"], reply["answer"])
    assert evidence["queriedAt"] == "2026-11-17T12:00:00"
    assert evidence["sources"] and any(s["dataUpdatedAt"] for s in evidence["sources"])
    return reply, evidence


def proposal():
    reply = api("/ai/chat", {"message": "把指标卡 OVERDUE（已逾期）移到第 1 位。"})
    assert reply["pending"], reply
    assert reply["pending"]["changes"][0]["after"] == "1", reply["pending"]
    return {"sessionId": reply["sessionId"], "proposalId": reply["pending"]["id"]}


def same_detail(evidence, expected_total):
    action = next(a for a in evidence["actions"] if a["url"].startswith("/?"))
    query = urllib.parse.urlsplit(action["url"]).query
    assert api("/todos?" + query)["total"] == expected_total, (action, expected_total)


assert api("/ai/status") == {"configured": True, "model": "gpt-6.1-sol"}
assert (
    api("/todos")["total"] == 100
), "Run business acceptance against the isolated fixture DB first"
api("/ai/chat", {"message": " "}, status=400)
before_log = max((row["id"] for row in api("/ai/logs")), default=0)

mine, counts = ask("我今天有多少条待办？", "get_todo_summary")
assert counts["conditions"]["owner"] == "演示用户"
mine_metrics = api("/metrics/summary?" + urllib.parse.urlencode({"owner": "演示用户"}))
assert all(mine_metrics[k] == v for k, v in counts["result"].items())
same_detail(counts, mine_metrics["TOTAL"])
print("PASS mine", counts["result"], flush=True)

proc, _ = ask("查询采购类、已逾期且高风险的任务。", "search_todos", mine["sessionId"])
result = next(e for e in proc["evidence"] if e["tool"] == "search_todos")
assert result["result"]["total"] == 3, result
same_detail(result, 3)
follow, inventory = ask(
    "按刚才的筛选条件，把业务类型换成库存。", "search_todos", proc["sessionId"]
)
assert follow["sessionId"] == proc["sessionId"]
assert all(
    inventory["conditions"][k] == v
    for k, v in {
        "businessType": "INVENTORY",
        "view": "OVERDUE",
        "riskLevel": "HIGH",
    }.items()
), inventory["conditions"]
same_detail(inventory, inventory["result"]["total"])
print(
    "PASS complex and multi-turn",
    result["result"]["total"],
    inventory["result"]["total"],
    flush=True,
)

_, trend = ask("查询最近 7 天按创建日期的新增趋势。", "get_metric")
assert trend["result"]["trend"] == api("/metrics/charts?trendDays=7")["trend"]
_, overdue = ask("查询最近7天逾期量及逾期趋势，不按创建日期。", "get_metric")
assert overdue["conditions"]["metric"] == "OVERDUE_TREND"
assert overdue["result"] == api("/metrics/overdue-trend?trendDays=7")
same_detail(overdue, overdue["result"]["total"])
print("PASS overdue due-date trend", overdue["result"], flush=True)
print(
    "PASS seven-day trend", [p["count"] for p in trend["result"]["trend"]], flush=True
)

_, link = ask("打开待审核询价页面。", "get_system_link")
external = next(a["url"] for a in link["actions"] if a["url"].startswith("http"))
parsed = urllib.parse.urlsplit(external)
assert (
    parsed.hostname == "127.0.0.1"
    and parsed.port == int(mock_port)
    and parsed.path == "/business/rfq"
)
with urllib.request.urlopen(external, timeout=5) as response:
    assert response.status == 200
assert any(
    r["operation_type"] == "SYSTEM_JUMP" and r["result"] == "RESOLVED"
    for r in api("/logs")
)
print("PASS RFQ link", parsed.path, flush=True)

draft, summary = ask(
    "按待回复供应商任务生成一段礼貌的供应商催办话术。", "generate_business_summary"
)
assert summary["conditions"]["kind"] == "FOLLOW_UP"
assert summary["conditions"]["businessType"] == "SUPPLIER"
assert len(draft["answer"]) > 20
print("PASS follow-up draft", summary["result"]["total"], flush=True)

config = api("/workbench/config")
try:
    cancel = proposal()
    assert api("/workbench/config") == config
    assert not api("/ai/config/decision", {**cancel, "confirm": False})["applied"]
    assert api("/workbench/config") == config
    api("/ai/config/decision", {**cancel, "confirm": True}, status=404)
    approve = proposal()
    assert api("/workbench/config") == config
    assert api("/ai/config/decision", {**approve, "confirm": True})["applied"]
    cards = api("/workbench/config")["cards"]
    assert min(cards, key=lambda card: card["order"])["key"] == "OVERDUE"
    api("/ai/config/decision", {**approve, "confirm": True}, status=404)
finally:
    if api("/workbench/config") != config:
        api("/workbench/config", config, "PUT")
print("PASS config before/after, cancel and replay", flush=True)

source = next(s for s in api("/data-sources") if s["type"] == "HTTP")
try:
    api(
        f"/data-sources/{source['id']}",
        {**source, "endpoint": f"http://127.0.0.1:{mock_port}/api/failure"},
        "PUT",
    )
    api(f"/data-sources/{source['id']}/sync", method="POST", status=502)
    _, partial = ask("汇总所有人的待办数据。", "get_todo_summary")
    assert any(
        s["name"] == source["name"] and s["status"] == "FAILED" and s["dataUpdatedAt"]
        for s in partial["sources"]
    )
    assert partial["result"]["TOTAL"] == 80
finally:
    api(f"/data-sources/{source['id']}", source, "PUT")
    api(f"/data-sources/{source['id']}/sync", method="POST")
print("PASS stale/partial source attribution", flush=True)

logged = {
    (row["tool_name"], row["status"])
    for row in api("/ai/logs")
    if row["id"] > before_log
}
assert {
    ("get_todo_summary", "SUCCESS"),
    ("search_todos", "SUCCESS"),
    ("get_metric", "SUCCESS"),
    ("get_system_link", "SUCCESS"),
    ("generate_business_summary", "SUCCESS"),
    ("propose_workbench_config", "PENDING_CONFIRMATION"),
    ("update_workbench_config", "APPLIED"),
    ("update_workbench_config", "CANCELLED"),
} <= logged, logged
print("PASS AI audit", sorted(logged))
