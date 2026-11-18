import { taskTitle } from "../state/presentation";
import { useRef, useState } from "react";
import { Button, Descriptions, Drawer, Table, Tag, Alert } from "antd";
import {
  get,
  businessLabels,
  riskLabels,
  statusLabels,
  type Todo,
  type Page,
} from "../api/business";
export default function TodoTable({
  data,
  loading,
  page,
  onPage,
}: {
  data?: Page;
  loading: boolean;
  page: number;
  onPage: (p: number) => void;
}) {
  const [detail, setDetail] = useState<Todo>();
  const [error, setError] = useState("");
  const [link, setLink] = useState("");
  const [resolving, setResolving] = useState(false);
  const request = useRef(0);
  const labels: Record<string, string> = {
    id: "任务编号",
    sourceId: "来源编号",
    sourceRecordId: "来源记录编号",
    businessNo: "业务单号",
    title: "任务名称",
    description: "任务说明",
    businessType: "业务类型",
    sourceSystem: "来源系统",
    sourcePage: "来源页面",
    status: "处理状态",
    riskLevel: "风险等级",
    priority: "优先级",
    owner: "负责人",
    businessOrg: "业务组织",
    businessUrl: "原始业务地址",
    createdAt: "创建时间",
    dueAt: "要求完成时间",
    sourceUpdatedAt: "来源更新时间",
    updatedAt: "入库更新时间",
    completedAt: "完成时间",
    overdue: "是否逾期",
    overdueMinutes: "逾期时长（分钟）",
  };
  function value(key: string, v: unknown) {
    if (v === null || v === undefined || v === "") return "—";
    if (key === "title") return taskTitle(String(v));
    if (key === "businessType") return businessLabels[String(v)] || String(v);
    if (key === "status") return statusLabels[String(v)] || String(v);
    if (key === "riskLevel") return riskLabels[String(v)] || String(v);
    if (key === "priority")
      return (
        ({ HIGH: "高", MEDIUM: "中", LOW: "低" } as Record<string, string>)[
          String(v)
        ] || String(v)
      );
    if (typeof v === "boolean") return v ? "是" : "否";
    return String(v).replace(/(\d{4}-\d{2}-\d{2})T/, "$1 ");
  }
  async function resolve(t: Todo) {
    const n = ++request.current;
    setResolving(true);
    setError("");
    setLink("");
    try {
      const r = await get<{ url: string }>("/system-links/resolve", {
        taskId: t.id,
      });
      if (n === request.current) setLink(r.url);
    } catch (e) {
      if (n === request.current) setError((e as Error).message);
    } finally {
      if (n === request.current) setResolving(false);
    }
  }
  return (
    <>
      <p className="muted">
        左右滑动表格，可查看要求完成时间、逾期时长和来源等完整字段。
      </p>
      <Table
        loading={loading}
        rowKey="id"
        dataSource={data?.items || []}
        className="todo-table"
        tableLayout="fixed"
        scroll={{ x: 1120 }}
        pagination={{
          current: page,
          pageSize: 20,
          total: data?.total || 0,
          showSizeChanger: false,
          onChange: onPage,
        }}
        columns={[
          {
            title: "业务 / 单号",
            width: 150,
            render: (_, t: Todo) => (
              <>
                <Tag>{businessLabels[t.businessType]}</Tag>
                <small>{t.businessNo}</small>
              </>
            ),
          },
          {
            title: "任务",
            width: 210,
            render: (_, t: Todo) => (
              <Button
                type="link"
                onClick={() => {
                  request.current++;
                  setResolving(false);
                  setDetail(t);
                  setLink("");
                  setError("");
                }}
              >
                {taskTitle(t.title)}
              </Button>
            ),
          },
          {
            title: "状态",
            width: 100,
            dataIndex: "status",
            render: (v: string) => statusLabels[v],
          },
          {
            title: "风险",
            width: 100,
            dataIndex: "riskLevel",
            render: (v: string) => (
              <Tag color={v === "HIGH" ? "red" : "blue"}>{riskLabels[v]}</Tag>
            ),
          },
          { title: "负责人", dataIndex: "owner", width: 110 },
          {
            title: "要求完成",
            width: 160,
            dataIndex: "dueAt",
            render: (v: string) => v.replace("T", " "),
          },
          {
            title: "逾期",
            width: 120,
            render: (_, t: Todo) =>
              t.overdue ? (
                <Tag color="orange">{t.overdueMinutes} 分钟</Tag>
              ) : (
                "—"
              ),
          },
          { title: "来源", dataIndex: "sourceSystem", width: 170 },
        ]}
      />
      <Drawer
        width="min(640px, 100vw)"
        title="待办详情与来源追踪"
        open={!!detail}
        onClose={() => {
          request.current++;
          setResolving(false);
          setDetail(undefined);
        }}
      >
        {detail && (
          <>
            <div className="detail-actions">
              <Button
                type="primary"
                loading={resolving}
                onClick={() => resolve(detail)}
              >
                生成业务深链接
              </Button>
              <p className="muted">
                根据当前单据生成跳转地址；生成后点击链接进入对应业务页面。
              </p>
              {error && <Alert type="error" message={error} />}
              {link && (
                <a href={link} target="_blank" rel="noreferrer">
                  打开业务页面 ↗
                </a>
              )}
            </div>
            <Descriptions
              column={1}
              bordered
              items={Object.entries(detail)
                .filter(([k]) => !["extraJson"].includes(k))
                .map(([k, v]) => ({
                  key: k,
                  label: labels[k] || k,
                  children: value(k, v),
                }))}
            />
            <details className="source-details">
              <summary>查看来源原文与扩展信息</summary>
              <p>来源任务名称：{detail.title}</p>
              <pre>{detail.extraJson || "无扩展信息"}</pre>
            </details>
          </>
        )}
      </Drawer>
    </>
  );
}
