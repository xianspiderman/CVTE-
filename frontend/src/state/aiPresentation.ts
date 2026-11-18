import type { AiReply, Evidence } from "../api/ai";

// Render facts from tool evidence, never infer a missing count from model prose.
export function answerText(reply: AiReply): string {
  if (reply.answer !== "已查询工作台。请查看下方结果、来源状态和明细入口。")
    return plainAnswer(reply.answer);
  return (
    reply.evidence.map(evidenceSummary).filter(Boolean).join("\n") ||
    reply.answer
  );
}
function evidenceSummary(e: Evidence): string {
  if (e.tool === "get_todo_summary") {
    const counts = e.result.counts ?? e.result;
    if (typeof counts.TOTAL !== "number") return "";
    const scope = e.conditions.owner
      ? String(e.conditions.owner)
      : "全部负责人";
    const details = [
      ["OVERDUE", "已逾期"],
      ["TODAY_DUE", "今日到期"],
      ["HIGH_RISK", "高风险"],
    ].flatMap(([key, label]) =>
      typeof counts[key] === "number" ? [`${label} ${counts[key]} 条`] : [],
    );
    return `${scope}当前有 ${counts.TOTAL} 条未完成待办${details.length ? "：" + details.join("，") : ""}。`;
  }
  if (e.tool === "search_todos" && typeof e.result.total === "number")
    return `找到 ${e.result.total} 条符合条件的任务，具体单据见下方。`;
  if (
    e.tool === "get_metric" &&
    e.conditions.metric === "OVERDUE_TREND" &&
    typeof e.result.total === "number"
  )
    return `所选到期日期范围内，当前仍逾期的任务共 ${e.result.total} 条；下方按到期日期列出。`;
  if (e.tool === "get_system_link")
    return e.actions.length
      ? "已找到业务入口，点击下方链接打开对应单据。"
      : "未找到可用的业务入口，请查看下方结果。";
  return "";
}
export function sourceIssue(s: Evidence["sources"][number]): string {
  const reason: Record<string, string> = {
    PARTIAL: "部分记录导入失败",
    FAILED: "最近同步或导入失败",
    NEVER: "尚未同步或导入",
  };
  return `${s.name}：${reason[s.status] || "状态未知"}`;
}

// Keep model text as escaped React text; remove only paired emphasis and quote prefixes.
export function plainAnswer(text: string): string {
  return text.replace(/\*\*([^*]+)\*\*/g, "$1").replace(/^[ \t]*>[ \t]?/gm, "");
}
