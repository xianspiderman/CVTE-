// Presentation only: source records and query values remain unchanged.
export function taskTitle(title: string) {
  return title.replace(/^脱敏验收任务 (\d+)$/, "任务 $1");
}
export const sourceStatus: Record<string, string> = {
  SUCCESS: "正常",
  PARTIAL: "部分数据导入失败",
  FAILED: "连接或导入失败",
  NEVER: "尚未同步",
};
export const viewNames: Record<string, string> = {
  ALL: "全部记录",
  TOTAL: "待办总数",
  MINE: "我的待办",
  TODAY_NEW: "今日新增",
  TODAY_DUE: "今日到期",
  OVERDUE: "已逾期",
  DUE_SOON: "即将逾期",
  HIGH_RISK: "高风险",
  COMPLETED: "已完成",
  PENDING_REVIEW: "待审核",
  PENDING_REPLY: "待回复",
  WEEK_COMPLETED: "本周完成",
  AVG_HOURS: "处理时长明细",
  ON_TIME_RATE: "完成率明细",
};
export function filterSummary(
  filters: Record<string, string>,
  defaultDays: number,
  defaultOwner: string,
) {
  const parts = [viewNames[filters.view || "ALL"] || "全部记录"];
  const business: Record<string, string> = {
    PROCUREMENT: "采购",
    INVENTORY: "库存",
    SUPPLIER: "供应商",
  };
  const risk: Record<string, string> = {
    HIGH: "高风险",
    MEDIUM: "中风险",
    LOW: "低风险",
  };
  const status: Record<string, string> = {
    PENDING: "待处理",
    PENDING_REVIEW: "待审核",
    PENDING_REPLY: "待回复",
    COMPLETED: "已完成",
  };
  if (filters.businessType)
    parts.push(business[filters.businessType] || filters.businessType);
  if (filters.riskLevel)
    parts.push(risk[filters.riskLevel] || filters.riskLevel);
  if (filters.sourceSystem) parts.push("来源：" + filters.sourceSystem);
  const owner = filters.owner ?? defaultOwner;
  if (filters.view === "MINE") parts.push("当前用户：演示用户");
  if (owner) parts.push("负责人：" + owner);
  else if (filters.view !== "MINE") parts.push("全部负责人");
  const days = filters.days ?? String(defaultDays);
  parts.push(days ? "最近" + days + "天创建" : "不限创建时间");
  if (filters.status)
    parts.push("状态：" + (status[filters.status] || filters.status));
  if (filters.createdDate) parts.push("创建日期：" + filters.createdDate);
  if (filters.dueDays) parts.push("最近" + filters.dueDays + "天到期");
  if (filters.dueDate) parts.push("到期日期：" + filters.dueDate);
  if (filters.keyword) parts.push("关键词：" + filters.keyword);
  return parts.join(" · ");
}
