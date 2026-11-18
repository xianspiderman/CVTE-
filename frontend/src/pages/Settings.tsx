import { useEffect, useState } from "react";
import {
  Alert,
  Button,
  Card,
  Input,
  InputNumber,
  Select,
  Switch,
  Table,
  Tabs,
  App as AntApp,
} from "antd";
import { useConfig } from "../state/config";
import {
  get,
  post,
  type Config,
  type Item,
  type Source,
} from "../api/business";
export default function Settings() {
  const { message } = AntApp.useApp();
  const { config, save } = useConfig();
  const [draft, setDraft] = useState<Config>();
  const [busy, setBusy] = useState(false);
  const [links, setLinks] = useState<Record<string, unknown>[]>([]);
  const [sources, setSources] = useState<Source[]>([]);
  const [sourceId, setSourceId] = useState<number>();
  const [pageCode, setPageCode] = useState("");
  const [template, setTemplate] = useState("");
  useEffect(() => {
    if (config) setDraft(structuredClone(config));
  }, [config]);
  useEffect(() => {
    Promise.all([
      get<Record<string, unknown>[]>("/system-links"),
      get<Source[]>("/data-sources"),
    ])
      .then(([l, s]) => {
        setLinks(l);
        setSources(s);
      })
      .catch((e) => message.error(e.message));
  }, []);
  function update(
    section: "navigation" | "cards" | "shortcuts",
    key: string,
    field: string,
    value: unknown,
  ) {
    setDraft((d) =>
      d
        ? {
            ...d,
            [section]: d[section].map((x) =>
              x.key === key ? { ...x, [field]: value } : x,
            ),
          }
        : d,
    );
  }
  async function persist() {
    if (!draft) return;
    setBusy(true);
    try {
      await save(draft);
      message.success("配置已保存到数据库");
    } catch (e) {
      message.error((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  if (!draft) return <Alert type="info" message="读取配置中" />;
  const table = (section: "navigation" | "cards" | "shortcuts") => (
    <Table
      rowKey="key"
      dataSource={draft[section]}
      pagination={false}
      scroll={{ x: 1200 }}
      columns={[
        { title: "标识", dataIndex: "key" },
        ...(section === "cards"
          ? [
              {
                title: "指标来源",
                render: (_: unknown, r: Item) => (
                  <Select
                    style={{ width: 160 }}
                    value={r.metric || r.key}
                    options={[
                      "TOTAL",
                      "TODAY_NEW",
                      "OVERDUE",
                      "DUE_SOON",
                      "HIGH_RISK",
                      "PENDING_REVIEW",
                      "PENDING_REPLY",
                      "WEEK_COMPLETED",
                      "AVG_HOURS",
                      "ON_TIME_RATE",
                    ].map((value) => ({ value }))}
                    onChange={(value) =>
                      update(section, r.key, "metric", value)
                    }
                  />
                ),
              },
            ]
          : []),
        {
          title: "显示",
          render: (_, r: Item) => (
            <Switch
              checked={r.visible}
              onChange={(v) => update(section, r.key, "visible", v)}
            />
          ),
        },
        {
          title: "名称",
          render: (_, r: Item) => (
            <Input
              value={r.name}
              onChange={(e) => update(section, r.key, "name", e.target.value)}
            />
          ),
        },
        {
          title: "排序",
          render: (_, r: Item) => (
            <InputNumber
              value={r.order}
              onChange={(v) => update(section, r.key, "order", v)}
            />
          ),
        },
        {
          title: "图标",
          render: (_, r: Item) => (
            <Select
              style={{ width: 100 }}
              value={r.icon}
              options={["app", "chart", "link"].map((value) => ({ value }))}
              onChange={(v) => update(section, r.key, "icon", v)}
            />
          ),
        },
        ...(section === "cards"
          ? [
              {
                title: "颜色",
                render: (_: unknown, r: Item) => (
                  <Input
                    type="color"
                    value={r.color}
                    onChange={(e) =>
                      update(section, r.key, "color", e.target.value)
                    }
                  />
                ),
              },
              {
                title: "时间范围（天）",
                render: (_: unknown, r: Item) => (
                  <InputNumber
                    min={1}
                    max={365}
                    value={r.days}
                    onChange={(v) => update(section, r.key, "days", v)}
                  />
                ),
              },
              {
                title: "刷新（秒）",
                render: (_: unknown, r: Item) => (
                  <InputNumber
                    min={10}
                    max={3600}
                    value={r.refreshSeconds}
                    onChange={(v) =>
                      update(section, r.key, "refreshSeconds", v)
                    }
                  />
                ),
              },
              {
                title: "点击行为",
                render: (_: unknown, r: Item) => (
                  <Select
                    style={{ width: 110 }}
                    value={r.clickBehavior}
                    options={[
                      { value: "DETAIL", label: "查看明细" },
                      { value: "NONE", label: "无操作" },
                    ]}
                    onChange={(v) => update(section, r.key, "clickBehavior", v)}
                  />
                ),
              },
            ]
          : [
              {
                title: "内部路径 / 外部链接",
                render: (_: unknown, r: Item) => (
                  <Input
                    value={r.path}
                    onChange={(e) =>
                      update(section, r.key, "path", e.target.value)
                    }
                  />
                ),
              },
              ...(section === "navigation"
                ? [
                    {
                      title: "上级菜单标识",
                      render: (_: unknown, r: Item) => (
                        <Input
                          value={r.parent}
                          onChange={(e) =>
                            update(section, r.key, "parent", e.target.value)
                          }
                        />
                      ),
                    },
                  ]
                : [
                    {
                      title: "AI 快捷提问",
                      render: (_: unknown, r: Item) => (
                        <Input
                          value={r.aiPrompt}
                          onChange={(e) =>
                            update(section, r.key, "aiPrompt", e.target.value)
                          }
                        />
                      ),
                    },
                  ]),
            ]),
      ]}
    />
  );
  return (
    <>
      <div className="heading">
        <h1>工作台配置</h1>
        <Button type="primary" loading={busy} onClick={() => void persist()}>
          保存工作台配置
        </Button>
      </div>
      <Alert
        message="在此手动编辑，点击“保存工作台配置”后生效。通过右侧 AI 修改卡片时，需要先确认提案。"
        type="info"
      />
      <Card className="preview">
        <div className="settings-grid">
          <label>
            即将逾期阈值（小时）
            <InputNumber
              min={1}
              max={168}
              value={draft.dueSoonHours}
              onChange={(v) => setDraft({ ...draft, dueSoonHours: v || 24 })}
            />
          </label>
          <label>
            默认创建时间范围（天）
            <InputNumber
              min={1}
              max={365}
              value={draft.defaultDays}
              onChange={(v) => setDraft({ ...draft, defaultDays: v || 30 })}
            />
          </label>
          <label>
            默认负责人
            <Input
              value={draft.defaultOwner}
              onChange={(e) =>
                setDraft({ ...draft, defaultOwner: e.target.value })
              }
            />
          </label>
          <label>
            默认视图
            <Select
              value={draft.defaultView}
              options={[
                { value: "LIST", label: "列表" },
                { value: "ANALYTICS", label: "统计分析" },
              ]}
              onChange={(v) => setDraft({ ...draft, defaultView: v })}
            />
          </label>
          <label>
            AI 面板展开{" "}
            <Switch
              checked={draft.aiExpanded}
              onChange={(v) => setDraft({ ...draft, aiExpanded: v })}
            />
          </label>
        </div>
      </Card>
      <Card className="preview">
        <Tabs
          items={[
            {
              key: "cards",
              label: "指标卡",
              children: table("cards"),
            },
            { key: "navigation", label: "导航", children: table("navigation") },
            {
              key: "shortcuts",
              label: "快捷入口",
              children: table("shortcuts"),
            },
          ]}
        />
      </Card>
      <Card className="preview" title="系统深链接模板">
        <Table
          rowKey="id"
          dataSource={links}
          pagination={false}
          columns={[
            { title: "来源 ID", dataIndex: "source_id" },
            { title: "页面代码", dataIndex: "page_code" },
            { title: "模板", dataIndex: "url_template" },
          ]}
        />
        <p>支持 {"${businessNo}、${pageCode}、${taskId}、${sourceSystem}"}</p>
        <div className="settings-grid">
          <Select
            placeholder="选择来源"
            options={sources.map((s) => ({ value: s.id, label: s.name }))}
            value={sourceId}
            onChange={setSourceId}
          />
          <Input
            placeholder="页面代码"
            value={pageCode}
            onChange={(e) => setPageCode(e.target.value)}
          />
          <Input
            placeholder="完整 HTTP(S) 模板"
            value={template}
            onChange={(e) => setTemplate(e.target.value)}
          />
          <Button
            onClick={async () => {
              try {
                await post("/system-links", {
                  sourceId,
                  pageCode,
                  urlTemplate: template,
                });
                setLinks(await get("/system-links"));
                message.success("链接模板已保存");
              } catch (e) {
                message.error((e as Error).message);
              }
            }}
          >
            保存链接模板
          </Button>
        </div>
      </Card>
    </>
  );
}
