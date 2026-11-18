import { sourceIssue } from "../state/aiPresentation";
import { taskTitle } from "../state/presentation";
import { Link } from "react-router-dom";
import { Alert, Tag } from "antd";
import { type Evidence } from "../api/ai";
import { businessLabels, riskLabels, statusLabels } from "../api/business";

export const metricLabels: Record<string, string> = {
  ALL: "全部记录",
  MINE: "我的待办",
  TOTAL: "待办总数",
  TODAY_NEW: "今日新增",
  TODAY_DUE: "今日到期",
  OVERDUE: "已逾期",
  DUE_SOON: "即将逾期",
  HIGH_RISK: "高风险",
  PENDING_REVIEW: "待审核",
  PENDING_REPLY: "待回复",
  WEEK_COMPLETED: "本周完成",
  COMPLETED: "已完成",
  AVG_HOURS: "平均处理时长",
  ON_TIME_RATE: "按时完成率",
  WEEK_ON_TIME_RATE: "本周按时完成率",
  TREND: "新增趋势",
  OVERDUE_TREND: "按到期日期的当前逾期分布",
};
const conditionLabels: Record<string, string> = {
  owner: "负责人",
  businessType: "业务类型",
  sourceSystem: "来源",
  days: "创建时间（天）",
  riskLevel: "风险",
  status: "状态",
  keyword: "关键词",
  view: "视图",
  metric: "指标",
  trendDays: "趋势天数",
  dueDays: "到期日期范围（天）",
  page: "页码",
  pageCode: "页面",
  taskId: "待办 ID",
  businessNo: "单号",
  kind: "摘要类型",
};
const valueLabels: Record<string, string> = {
  ...metricLabels,
  ...businessLabels,
  ...riskLabels,
  ...statusLabels,
  FOCUS: "工作重点",
  WORK_SUMMARY: "工作摘要",
  FOLLOW_UP: "催办草稿",
};
const sourceLabels: Record<string, string> = {
  SUCCESS: "成功",
  PARTIAL: "部分失败",
  FAILED: "失败",
  NEVER: "尚未接入数据",
  UNKNOWN: "未知来源",
};
const time = (value: string) => value.replace("T", " ").slice(0, 19);
const quantity = (key: string, value: number | null) =>
  value === null
    ? "无样本"
    : value +
      (key.endsWith("_RATE") ? "%" : key === "AVG_HOURS" ? " 小时" : " 条");

export default function AiEvidence({ evidence }: { evidence: Evidence }) {
  const { result, conditions, sources, actions } = evidence;
  const metrics = Object.entries(result.counts ?? result).filter(
    ([key, value]) =>
      key in metricLabels && (value === null || typeof value === "number"),
  );
  const incomplete = sources.filter((s) => s.status !== "SUCCESS");
  return (
    <section className="ai-evidence">
      <strong>{evidence.label}</strong>
      <small>
        条件{" "}
        {Object.entries(conditions)
          .map(
            ([key, value]) =>
              `${conditionLabels[key] ?? key}=${valueLabels[value] ?? value}`,
          )
          .join(" · ") || "全范围"}
      </small>
      {result.counts && conditions.view && (
        <small>
          总体指标不限定“{valueLabels[conditions.view] ?? conditions.view}
          ”视图；下方任务明细按该视图筛选。
        </small>
      )}
      {metrics.length > 0 && (
        <dl className="ai-facts">
          {metrics.map(([key, value]) => (
            <div key={key}>
              <dt>{metricLabels[key]}</dt>
              <dd>{quantity(key, value as number | null)}</dd>
            </div>
          ))}
        </dl>
      )}
      {result.total !== undefined && (
        <p>
          匹配 {result.total} 条
          {result.items && result.total > result.items.length
            ? "，下方为本次返回记录"
            : ""}
        </p>
      )}
      {!!result.items?.length && (
        <ul className="ai-data-list">
          {result.items.map((item) => (
            <li key={item.id}>
              <b>{taskTitle(item.title)}</b>
              <small>
                {item.businessNo} · {item.owner || "未分配"} ·{" "}
                {statusLabels[item.status]} · {riskLabels[item.riskLevel]}
              </small>
              <small>
                {item.overdue ? "已逾期 · 到期时间" : "到期时间"}{" "}
                {time(item.dueAt)}
              </small>
            </li>
          ))}
        </ul>
      )}
      {result.trend && (
        <details open>
          <summary>{result.trendMeaning || "趋势明细"}</summary>
          <ul className="ai-data-list">
            {result.trend.map((point) => (
              <li key={point.date}>
                {point.date} · {point.count} 条
              </li>
            ))}
          </ul>
          {Object.entries({ ...result.business, ...result.risk }).map(
            ([key, value]) => (
              <Tag key={key}>
                {valueLabels[key] ?? key} {value}
              </Tag>
            ),
          )}
        </details>
      )}
      {!!result.unavailable?.length && (
        <p>缺少链接模板的单据 {result.unavailable.join("、")}</p>
      )}
      {incomplete.length > 0 && (
        <Alert
          type="warning"
          showIcon
          message={`${incomplete.map(sourceIssue).join("；")}。结果仅含已入库记录。`}
        />
      )}
      <div className="ai-source">
        {sources.length === 0 && <small>暂无已配置的数据来源</small>}
        {sources.map((s) => (
          <small key={s.name}>
            来源：{s.name} · 数据更新：
            {s.dataUpdatedAt ? time(s.dataUpdatedAt) : "暂无"}
          </small>
        ))}
        <details>
          <summary>查询与同步详情</summary>
          <small>
            业务查询时间：{time(evidence.queriedAt)} · Asia/Shanghai
          </small>
          {sources.map((s) => (
            <small key={s.name}>
              {s.name} · {sourceLabels[s.status] ?? s.status} · 最近同步/导入：
              {s.lastAttempt ? time(s.lastAttempt) : "暂无记录"}
            </small>
          ))}
        </details>
      </div>
      <div className="ai-actions">
        {actions.map((a) =>
          a.url.startsWith("/") && !a.url.startsWith("//") ? (
            <Link
              key={a.label + a.url}
              to={a.url.split("#")[0] + "#todo-results"}
            >
              {a.label}
            </Link>
          ) : /^https?:\/\//.test(a.url) ? (
            <a
              key={a.label + a.url}
              href={a.url}
              target="_blank"
              rel="noreferrer"
            >
              {a.label} ↗
            </a>
          ) : null,
        )}
      </div>
    </section>
  );
}
