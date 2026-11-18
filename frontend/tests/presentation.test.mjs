import { test } from "node:test";
import assert from "node:assert/strict";
import { filterSummary, taskTitle } from "../src/state/presentation.ts";

test("summaries describe effective defaults and explicit overrides", () => {
  assert.equal(
    filterSummary({ view: "MINE" }, 30, ""),
    "我的待办 · 当前用户：演示用户 · 最近30天创建",
  );
  assert.equal(
    filterSummary({ view: "OVERDUE" }, 30, "演示用户"),
    "已逾期 · 负责人：演示用户 · 最近30天创建",
  );
  assert.equal(
    filterSummary(
      {
        owner: "",
        days: "",
        businessType: "PROCUREMENT",
        riskLevel: "HIGH",
        dueDays: "7",
      },
      30,
      "演示用户",
    ),
    "全部记录 · 采购 · 高风险 · 全部负责人 · 不限创建时间 · 最近7天到期",
  );
});
test("only known fixture titles receive a display alias", () => {
  assert.equal(taskTitle("脱敏验收任务 019"), "任务 019");
  assert.equal(taskTitle("供应商回复待确认"), "供应商回复待确认");
  assert.equal(taskTitle("客户要求脱敏验收任务"), "客户要求脱敏验收任务");
});
