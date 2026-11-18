import { test } from "node:test";
import assert from "node:assert/strict";
import {
  answerText,
  sourceIssue,
  plainAnswer,
} from "../src/state/aiPresentation.ts";
const reply = (
  evidence,
  answer = "已查询工作台。请查看下方结果、来源状态和明细入口。",
) => ({ answer, evidence });
test("summary uses tool counts and preserves the owner scope", () => {
  assert.equal(
    answerText(
      reply([
        {
          tool: "get_todo_summary",
          conditions: { owner: "演示用户" },
          result: { TOTAL: 40, OVERDUE: 10, TODAY_DUE: 8, HIGH_RISK: 4 },
        },
      ]),
    ),
    "演示用户当前有 40 条未完成待办：已逾期 10 条，今日到期 8 条，高风险 4 条。",
  );
});
test("missing facts are not invented and zero results remain explicit", () => {
  assert.equal(
    answerText(
      reply([
        { tool: "get_todo_summary", conditions: {}, result: { TOTAL: 0 } },
      ]),
    ),
    "全部负责人当前有 0 条未完成待办。",
  );
  assert.equal(
    answerText(
      reply([{ tool: "search_todos", conditions: {}, result: { total: 0 } }]),
    ),
    "找到 0 条符合条件的任务，具体单据见下方。",
  );
  assert.equal(answerText(reply([])), reply([]).answer);
});
test("confirmation, failure and business summary answers are preserved", () => {
  for (const text of [
    "请核对以下配置变更，然后选择确认或取消。",
    "本轮工具未取得可用结果",
    "请供应商确认交期",
  ])
    assert.equal(answerText(reply([], text)), text);
});
test("source notices distinguish partial imports and unavailable sources", () => {
  assert.equal(
    sourceIssue({ name: "库存", status: "PARTIAL" }),
    "库存：部分记录导入失败",
  );
  assert.equal(
    sourceIssue({ name: "采购", status: "FAILED" }),
    "采购：最近同步或导入失败",
  );
  assert.equal(
    sourceIssue({ name: "供应商", status: "NEVER" }),
    "供应商：尚未同步或导入",
  );
});

test("plain business summaries remove emphasis and quote markers without altering facts", () => {
  assert.equal(
    plainAnswer("查到 **4 条任务**。\n\n**催办话术**\n> 您好，请回复。"),
    "查到 4 条任务。\n\n催办话术\n您好，请回复。",
  );
  assert.equal(
    plainAnswer("2 * 3 = 6；未配对 **；a > b"),
    "2 * 3 = 6；未配对 **；a > b",
  );
});

test("overdue metric answer describes due-date scope using the tool total", () => {
  assert.equal(
    answerText(
      reply([
        {
          tool: "get_metric",
          conditions: { metric: "OVERDUE_TREND" },
          result: { total: 20 },
        },
      ]),
    ),
    "所选到期日期范围内，当前仍逾期的任务共 20 条；下方按到期日期列出。",
  );
});
