import { taskTitle, sourceStatus, filterSummary } from "../state/presentation";
import {
  useEffect,
  useState,
  useCallback,
  lazy,
  Suspense,
  useRef,
} from "react";
import { useSearchParams, useLocation, Link } from "react-router-dom";
import {
  Alert,
  Button,
  Card,
  Input,
  Select,
  Segmented,
  Space,
  Spin,
  Tag,
} from "antd";
import {
  get,
  type Page,
  type Charts,
  type Source,
  businessLabels,
  riskLabels,
} from "../api/business";
import { useConfig } from "../state/config";
import {
  iconGlyph,
  metricSource,
  refreshGroups,
} from "../state/workbenchPolicy";
import TodoTable from "../components/TodoTable";
const BusinessCharts = lazy(() => import("../components/BusinessCharts"));
const views = [
  ["ALL", "全部记录"],
  ["MINE", "我的待办"],
  ["TODAY_NEW", "今日新增"],
  ["TODAY_DUE", "今日到期"],
  ["OVERDUE", "已逾期"],
  ["DUE_SOON", "即将逾期"],
  ["HIGH_RISK", "高风险"],
  ["COMPLETED", "已完成"],
  ["TOTAL", "待办总数"],
  ["PENDING_REVIEW", "待审核"],
  ["PENDING_REPLY", "待回复"],
  ["WEEK_COMPLETED", "本周完成"],
  ["AVG_HOURS", "处理时长明细"],
  ["ON_TIME_RATE", "完成率明细"],
];
export default function Dashboard({
  businessType,
  analytics = false,
  onPrompt,
}: {
  businessType?: string;
  analytics?: boolean;
  onPrompt?: (prompt: string) => void;
}) {
  const { config } = useConfig();
  const [params, setParams] = useSearchParams();
  const location = useLocation();
  const [data, setData] = useState<Page>();
  const [risk, setRisk] = useState<Page>();
  const [metrics, setMetrics] = useState<Record<string, number | null>>({});
  const [charts, setCharts] = useState<Charts>();
  const [sources, setSources] = useState<Source[]>([]);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [mode, setMode] = useState(
    analytics ? "ANALYTICS" : config?.defaultView || "LIST",
  );
  const serial = useRef(0);
  const results = useRef<HTMLDivElement>(null);
  const [selection, setSelection] = useState("");
  const focusResults = () => {
    results.current?.scrollIntoView({ behavior: "instant", block: "start" });
    results.current?.focus({ preventScroll: true });
  };
  useEffect(() => {
    if (location.hash === "#todo-results") {
      setMode("LIST");
      setSelection("AI 查询明细");
      results.current?.scrollIntoView({ behavior: "instant", block: "start" });
      results.current?.focus({ preventScroll: true });
    }
  }, [location.key, location.hash]);
  const filters = Object.fromEntries(params);
  const page = Number(filters.page || 1);
  const change = (k: string, v: string) => {
    setSelection("");
    const next = new URLSearchParams(params);
    v || k === "owner" ? next.set(k, v) : next.delete(k);
    next.delete("page");
    setParams(next);
  };
  const load = useCallback(
    async (includeMetrics = true) => {
      if (!config) return;
      const n = ++serial.current;
      setBusy(true);
      setError("");
      const f = {
        days: config.defaultDays,
        owner: config.defaultOwner,
        ...Object.fromEntries(params),
        ...(businessType ? { businessType } : {}),
      };
      try {
        const [list, c, s, r, ...groups] = await Promise.all([
          get<Page>("/todos", f),
          get<Charts>("/metrics/charts", { ...f, trendDays: 7 }),
          get<Source[]>("/data-sources"),
          get<Page>("/todos", { ...f, view: "HIGH_RISK", page: 1 }),
          ...[
            ...new Set(
              includeMetrics
                ? config.cards.filter((x) => x.visible).map((c) => c.days)
                : [],
            ),
          ].map(async (days) => ({
            days,
            result: await get<Record<string, number | null>>(
              "/metrics/summary",
              {
                ...f,
                days,
              },
            ),
          })),
        ]);
        if (n !== serial.current) return;
        setData(list);
        setCharts(c);
        setSources(s);
        setRisk(r);
        if (includeMetrics)
          setMetrics(
            Object.fromEntries(
              config.cards
                .filter((c) => c.visible)
                .map((c) => [
                  c.key,
                  groups.find((g) => g.days === c.days)?.result[
                    metricSource(c)
                  ] ?? null,
                ]),
            ),
          );
      } catch (e) {
        if (n === serial.current) {
          setError((e as Error).message);
          setData(undefined);
          setCharts(undefined);
          setRisk(undefined);
          setMetrics({});
        }
      } finally {
        if (n === serial.current) setBusy(false);
      }
    },
    [params, businessType, config],
  );
  useEffect(() => {
    void load();
    const timer = setInterval(() => void load(false), 60000);
    let cancelled = false;
    const timers = refreshGroups(config?.cards || []).map((group) =>
      setInterval(async () => {
        try {
          const values = await get<Record<string, number | null>>(
            "/metrics/summary",
            {
              owner: config?.defaultOwner,
              ...Object.fromEntries(params),
              ...(businessType ? { businessType } : {}),
              days: group.days,
            },
          );
          if (!cancelled)
            setMetrics((previous) => ({
              ...previous,
              ...Object.fromEntries(
                group.cards.map((c) => [c.key, values[metricSource(c)]]),
              ),
            }));
        } catch (e) {
          if (!cancelled) {
            setError((e as Error).message);
            setMetrics((previous) => ({
              ...previous,
              ...Object.fromEntries(group.cards.map((c) => [c.key, null])),
            }));
          }
        }
      }, group.seconds * 1000),
    );
    return () => {
      cancelled = true;
      clearInterval(timer);
      timers.forEach(clearInterval);
      serial.current++;
    };
  }, [load, config, params, businessType]);
  useEffect(
    () => setMode(analytics ? "ANALYTICS" : config?.defaultView || "LIST"),
    [analytics, config?.defaultView],
  );
  const selectChart = (key: string, value: string) => change(key, value);
  return (
    <>
      <div className="heading">
        <div>
          <div className="eyebrow">SUPPLY CHAIN WORKSPACE</div>
          <h1>
            {businessType
              ? businessLabels[businessType] + "业务待办"
              : analytics
                ? "数据分析"
                : "我的工作台"}
          </h1>
          <p>
            业务时间：{data?.now?.replace("T", " ") || "读取中"} · Asia/Shanghai
            · 演示用户
          </p>
        </div>
        <Button loading={busy} onClick={() => void load()}>
          刷新数据
        </Button>
      </div>
      {error && <Alert showIcon type="error" message={error} />}{" "}
      {sources.some((s) =>
        ["FAILED", "PARTIAL", "NEVER"].includes(s.syncStatus),
      ) && (
        <Alert
          type="warning"
          showIcon
          message={
            "数据完整性提示：" +
            sources
              .filter((s) =>
                ["FAILED", "PARTIAL", "NEVER"].includes(s.syncStatus),
              )
              .map(
                (s) =>
                  `${s.name}（${sourceStatus[s.syncStatus] || s.syncStatus}）`,
              )
              .join("、") +
            "；当前显示已入库数据"
          }
        />
      )}
      <div className="metrics">
        {config?.cards
          .filter((c) => c.visible)
          .sort((a, b) => a.order - b.order)
          .map((c) => (
            <Card
              role={c.clickBehavior === "DETAIL" ? "button" : undefined}
              tabIndex={c.clickBehavior === "DETAIL" ? 0 : undefined}
              aria-label={c.name + "指标"}
              onKeyDown={(e) => {
                if (e.key === "Enter") e.currentTarget.click();
              }}
              key={c.key}
              hoverable={c.clickBehavior === "DETAIL"}
              onClick={() => {
                if (c.clickBehavior === "DETAIL") {
                  const p = new URLSearchParams(params);
                  p.set("view", metricSource(c));
                  p.set("days", String(c.days));
                  p.delete("page");
                  setParams(p);
                  setSelection(c.name);
                  focusResults();
                }
              }}
            >
              <span>
                {iconGlyph(c.icon)} {c.name}
              </span>
              <strong style={{ color: c.color }}>
                {metrics[c.key] ?? "—"}
                {metrics[c.key] == null
                  ? ""
                  : metricSource(c) === "ON_TIME_RATE"
                    ? "%"
                    : metricSource(c) === "AVG_HOURS"
                      ? "h"
                      : ""}
              </strong>
              <small>
                最近 {c.days} 天创建 ·{" "}
                {c.clickBehavior === "DETAIL" ? "查看下方任务" : "仅展示"}
              </small>
            </Card>
          ))}
      </div>
      <div
        id="todo-results"
        ref={results}
        tabIndex={-1}
        className="results-anchor"
      >
        {selection && (
          <Alert
            type="info"
            showIcon
            message={`已选择「${selection}」，下方显示对应任务。`}
            closable
            onClose={() => setSelection("")}
          />
        )}
        <Card className="filter-card">
          <Space wrap>
            <Select
              aria-label="待办视图"
              value={filters.view || "ALL"}
              style={{ width: 145 }}
              onChange={(v) => change("view", v)}
              options={views.map(([value, label]) => ({ value, label }))}
            />
            {!businessType && (
              <Select
                allowClear
                aria-label="业务类型"
                placeholder="业务类型"
                value={filters.businessType}
                style={{ width: 130 }}
                options={Object.entries(businessLabels).map(
                  ([value, label]) => ({
                    value,
                    label,
                  }),
                )}
                onChange={(v) => change("businessType", v || "")}
              />
            )}
            <Select
              allowClear
              aria-label="来源系统"
              placeholder="来源系统"
              value={filters.sourceSystem}
              style={{ width: 170 }}
              options={sources.map((s) => ({ value: s.name, label: s.name }))}
              onChange={(v) => change("sourceSystem", v || "")}
            />
            <Select
              allowClear
              aria-label="风险等级"
              placeholder="风险等级"
              style={{ width: 120 }}
              value={filters.riskLevel}
              options={Object.entries(riskLabels).map(([value, label]) => ({
                value,
                label,
              }))}
              onChange={(v) => change("riskLevel", v || "")}
            />
            <Input
              aria-label="负责人"
              placeholder="负责人（精确）"
              value={filters.owner ?? config?.defaultOwner ?? ""}
              onChange={(e) => change("owner", e.target.value)}
              style={{ width: 160 }}
            />
            <Input.Search
              aria-label="关键词"
              placeholder="标题 / 单号 / 描述"
              key={filters.keyword || ""}
              defaultValue={filters.keyword}
              onSearch={(v) => change("keyword", v)}
              style={{ width: 220 }}
            />
            <Button
              onClick={() => {
                setParams({});
                setSelection("");
              }}
            >
              重置筛选
            </Button>
          </Space>
          <p className="muted">
            当前范围：
            {filterSummary(
              { ...filters, ...(businessType ? { businessType } : {}) },
              config?.defaultDays || 30,
              config?.defaultOwner || "",
            )}
          </p>
        </Card>
      </div>
      <div className="section-head">
        <h2>统一业务记录 · {data?.total ?? "—"} 条</h2>
        <Segmented
          value={mode}
          options={[
            { value: "LIST", label: "列表视图" },
            { value: "ANALYTICS", label: "统计分析" },
          ]}
          onChange={setMode}
        />
      </div>
      {mode === "ANALYTICS" && charts && (
        <Card>
          <p className="muted">
            当前筛选结果按创建日期、业务类型和风险等级统计。点击图表或下方标签，可继续筛选任务。创建日期分布不代表每天的逾期数量。
          </p>
          <Suspense fallback={<Spin />}>
            <BusinessCharts data={charts} onSelect={selectChart} />
          </Suspense>
        </Card>
      )}
      <Card>
        <TodoTable
          data={data}
          loading={busy}
          page={page}
          onPage={(p) => {
            const next = new URLSearchParams(params);
            next.set("page", String(p));
            setParams(next);
          }}
        />
      </Card>
      {!businessType && !analytics && (
        <>
          <div className="section-head">
            <h2>风险关注</h2>
            <Tag color="red">未完成高风险 {risk?.total ?? "—"} 条</Tag>
          </div>
          <Card>
            {risk?.items.slice(0, 5).map((t) => (
              <p key={t.id}>
                <Tag color="red">{riskLabels[t.riskLevel]}</Tag>
                {taskTitle(t.title)} · {t.owner}{" "}
                <Button
                  type="link"
                  onClick={() => {
                    change("view", "HIGH_RISK");
                    setSelection("高风险");
                    focusResults();
                  }}
                >
                  筛选高风险任务
                </Button>
              </p>
            ))}
            {risk?.total === 0 && "暂无高风险任务"}
          </Card>
          <Card className="preview" title="快捷入口">
            <div className="shortcuts">
              {config?.shortcuts
                .filter((s) => s.visible)
                .sort((a, b) => a.order - b.order)
                .map((s) => (
                  <div className="shortcut" key={s.key}>
                    {s.path?.startsWith("/") ? (
                      <Link to={s.path}>
                        {iconGlyph(s.icon)} {s.name}
                      </Link>
                    ) : (
                      <a href={s.path} target="_blank" rel="noreferrer">
                        {iconGlyph(s.icon)} {s.name}
                      </a>
                    )}
                    {s.aiPrompt?.trim() && onPrompt && (
                      <Button
                        type="link"
                        size="small"
                        onClick={() => onPrompt(s.aiPrompt!)}
                      >
                        问 AI
                      </Button>
                    )}
                  </div>
                ))}
            </div>
          </Card>
        </>
      )}
      <p className="muted">
        来源更新时间：
        {sources
          .map((s) => s.name + " " + (s.lastSyncAt || "未同步"))
          .join(" · ")}
      </p>
    </>
  );
}
