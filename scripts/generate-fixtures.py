#!/usr/bin/env python3
"""Deterministic, anonymized acceptance inputs; never inserts directly into DB."""
import csv, json
from datetime import datetime, timedelta
from pathlib import Path

root = Path(__file__).resolve().parents[1]
out = root / "test-data"
now = datetime(2026, 11, 17, 12)
rows = []
for i in range(100):
    created = now.replace(hour=8) if 20 <= i < 35 else now - timedelta(days=3 + (i % 4))
    due = (
        now - timedelta(days=1)
        if i < 20
        else now.replace(hour=17) if i < 35 else now + timedelta(days=2)
    )
    if i == 35:
        due = now + timedelta(hours=24)
    status = (
        "PENDING_REVIEW"
        if 40 <= i < 50
        else "PENDING_REPLY" if 50 <= i < 60 else "COMPLETED" if i >= 80 else "PENDING"
    )
    complete = now - timedelta(hours=2) if i >= 80 else None
    if complete:
        due = complete + timedelta(hours=1) if i < 90 else complete - timedelta(hours=1)
    rows.append(
        dict(
            sourceRecordId=f"ACCEPT-{i:03}",
            businessNo=f"DEMO-{i:03}",
            title=f"脱敏验收任务 {i:03}",
            description="固定业务验收样例",
            businessType=["PROCUREMENT", "INVENTORY", "SUPPLIER"][i % 3],
            sourcePage=["rfq", "purchase", "stock", "supplier", "review"][i % 5],
            status=status,
            riskLevel="HIGH" if i < 8 else "MEDIUM" if i < 40 else "LOW",
            priority="HIGH" if i < 8 else "MEDIUM",
            owner="演示用户" if i % 2 == 0 else "演示同事",
            businessOrg="演示事业部",
            businessUrl="",
            extraJson='{"sample":true}',
            createdAt=created.isoformat(),
            dueAt=due.isoformat(),
            sourceUpdatedAt=now.isoformat(),
            completedAt=complete.isoformat() if complete else "",
        )
    )
http = [dict(row) for row in rows[:60]]
for row in http:
    row["id"] = row.pop("sourceRecordId")
(out / "acceptance-http.json").write_text(
    json.dumps(http, ensure_ascii=False, indent=2) + "\n"
)
errors = [
    {**rows[60], "sourceRecordId": "BAD-1", "title": ""},
    {**rows[61], "sourceRecordId": "BAD-2", "dueAt": "not-a-date"},
    {**rows[62], "sourceRecordId": "BAD-3", "riskLevel": "CRITICAL"},
]
with (out / "acceptance.csv").open("w") as f:
    writer = csv.DictWriter(f, fieldnames=list(rows[0]), lineterminator="\n")
    writer.writeheader()
    writer.writerows(rows[60:] + errors)
(out / "acceptance-expected.json").write_text(
    json.dumps(
        {
            "fixedNow": "2026-11-17T04:00:00Z",
            "timezone": "Asia/Shanghai",
            "stored": 100,
            "httpRows": 60,
            "fileRows": 40,
            "errorRows": 3,
            "TOTAL": 80,
            "TODAY_NEW": 15,
            "TODAY_DUE": 15,
            "OVERDUE": 20,
            "DUE_SOON": 15,
            "HIGH_RISK": 8,
            "PENDING_REVIEW": 10,
            "PENDING_REPLY": 10,
            "WEEK_COMPLETED": 20,
            "COMPLETED": 20,
            "ON_TIME_RATE": 50.0,
        },
        ensure_ascii=False,
        indent=2,
    )
    + "\n"
)
print("Generated deterministic HTTP/CSV fixtures; no database writes")
