import { useEffect, useState } from "react";
import { Alert, Button, Card, Table, Tabs } from "antd";
import { get } from "../api/business";
export default function Logs() {
  const [rows, setRows] = useState<Record<string, unknown>[]>([]);
  const [aiRows, setAiRows] = useState<Record<string, unknown>[]>([]);
  const [aiError, setAiError] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  async function load() {
    setBusy(true);
    const [operations, ai] = await Promise.allSettled([
      get<Record<string, unknown>[]>("/logs"),
      get<Record<string, unknown>[]>("/ai/logs"),
    ]);
    if (operations.status === "fulfilled") setRows(operations.value);
    if (ai.status === "fulfilled") setAiRows(ai.value);
    setError(operations.status === "rejected" ? operations.reason.message : "");
    setAiError(ai.status === "rejected" ? ai.reason.message : "");
    setBusy(false);
  }
  useEffect(() => {
    void load();
  }, []);
  return (
    <>
      <div className="heading">
        <h1>操作日志</h1>
        <Button onClick={() => void load()}>刷新</Button>
      </div>
      <Card>
        <Tabs
          items={[
            {
              key: "business",
              label: "业务操作",
              children: (
                <>
                  {error && <Alert type="error" message={error} />}
                  <Table
                    rowKey="id"
                    loading={busy}
                    dataSource={rows}
                    columns={Object.entries({
                      id: "ID",
                      operation_type: "操作",
                      target: "目标",
                      result: "结果",
                      created_at: "时间",
                    }).map(([dataIndex, title]) => ({ title, dataIndex }))}
                  />
                </>
              ),
            },
            {
              key: "ai",
              label: "AI 调用",
              children: (
                <>
                  {aiError && <Alert type="error" message={aiError} />}
                  <Table
                    rowKey="id"
                    loading={busy}
                    dataSource={aiRows}
                    columns={Object.entries({
                      id: "ID",
                      tool_name: "工具",
                      status: "结果",
                      duration_ms: "耗时（ms）",
                      error_message: "错误",
                      created_at: "时间",
                    }).map(([dataIndex, title]) => ({ title, dataIndex }))}
                  />
                </>
              ),
            },
          ]}
        />
      </Card>
    </>
  );
}
