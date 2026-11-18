import { sourceStatus } from "../state/presentation";
import { useEffect, useState } from "react";
import {
  Alert,
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Upload,
  App as AntApp,
} from "antd";
import { get, post, put, type Source, type Batch } from "../api/business";
export default function DataSources() {
  const { message } = AntApp.useApp();
  const [rows, setRows] = useState<Source[]>([]);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [edit, setEdit] = useState<Partial<Source>>();
  const [result, setResult] = useState<Batch>();
  const [batches, setBatches] = useState<Batch[]>([]);
  const [form] = Form.useForm();
  const sourceType = Form.useWatch("type", form) ?? edit?.type ?? "HTTP";
  const isHttp = sourceType === "HTTP";
  async function load() {
    setBusy(true);
    try {
      setRows(await get<Source[]>("/data-sources"));
      setBatches(await get<Batch[]>("/imports"));
      setError("");
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  useEffect(() => {
    void load();
  }, []);
  function open(s: Partial<Source>) {
    setEdit(s);
    form.setFieldsValue({
      ...s,
      mappingText: JSON.stringify(s.mappingConfig || {}, null, 2),
    });
  }
  async function save() {
    try {
      const v = await form.validateFields();
      const body = { ...v, mappingConfig: JSON.parse(v.mappingText) };
      if (edit?.id) await put("/data-sources/" + edit.id, body);
      else await post("/data-sources", body);
      setEdit(undefined);
      await load();
    } catch (e) {
      message.error((e as Error).message || "检查配置字段");
    }
  }
  async function sync(s: Source) {
    setBusy(true);
    try {
      setResult(await post<Batch>(`/data-sources/${s.id}/sync`));
      setError("");
    } catch (e) {
      setError((e as Error).message);
    } finally {
      try {
        setRows(await get<Source[]>("/data-sources"));
        setBatches(await get<Batch[]>("/imports"));
      } catch (e) {
        setError((e as Error).message);
      } finally {
        setBusy(false);
      }
    }
  }
  async function upload(s: Source, file: File) {
    setBusy(true);
    try {
      const data = new FormData();
      data.append("sourceId", String(s.id));
      data.append("file", file);
      setResult(await post<Batch>("/imports", data));
      await load();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
    return false;
  }
  return (
    <>
      <div className="heading">
        <h1>数据源配置与接入</h1>
        <Button
          type="primary"
          onClick={() =>
            open({
              type: "HTTP",
              requestMethod: "GET",
              timeoutMs: 3000,
              refreshInterval: 30,
              enabled: false,
            })
          }
        >
          新增数据源
        </Button>
      </div>
      <Alert
        type="info"
        message="仅访问项目本地 Mock；HTTP 来源可启用自动刷新，间隔单位为分钟。文件手动导入，支持 UTF-8 CSV / XLS / XLSX。"
      />
      {error && (
        <Alert
          type="error"
          message={error}
          closable
          onClose={() => setError("")}
        />
      )}
      <Card className="preview">
        <Table
          rowKey="id"
          loading={busy}
          dataSource={rows}
          pagination={false}
          scroll={{ x: 800 }}
          columns={[
            { title: "来源", dataIndex: "name" },
            { title: "接入类型", dataIndex: "type" },
            {
              title: "状态",
              render: (_, s: Source) => (
                <>
                  <Tag color={s.syncStatus === "SUCCESS" ? "green" : "orange"}>
                    {sourceStatus[s.syncStatus] || s.syncStatus}
                  </Tag>
                  <p>{s.lastError}</p>
                  <small>{s.lastSyncAt}</small>
                </>
              ),
            },
            {
              title: "刷新",
              render: (_, s: Source) =>
                s.type === "FILE"
                  ? "手动导入"
                  : `${s.enabled ? "自动" : "手动"} / ${s.refreshInterval} 分钟`,
            },
            {
              title: "操作",
              render: (_, s: Source) => (
                <Space>
                  <Button onClick={() => open(s)}>配置</Button>
                  {s.type === "HTTP" ? (
                    <Button disabled={busy} onClick={() => void sync(s)}>
                      同步
                    </Button>
                  ) : (
                    <Upload
                      accept=".csv,.xlsx,.xls"
                      showUploadList={false}
                      beforeUpload={(f) => upload(s, f)}
                    >
                      <Button disabled={busy}>导入文件</Button>
                    </Upload>
                  )}
                </Space>
              ),
            },
          ]}
        />
      </Card>
      <Card className="preview" title="导入 / 同步批次">
        <Table
          rowKey="id"
          dataSource={batches}
          columns={[
            { title: "批次", dataIndex: "id" },
            { title: "文件 / HTTP", dataIndex: "file_name" },
            { title: "成功（含更新）", dataIndex: "success_count" },
            { title: "更新", dataIndex: "updated_count" },
            { title: "重复 / 旧版本", dataIndex: "duplicate_count" },
            { title: "错误", dataIndex: "failure_count" },
            {
              title: "操作",
              render: (_, b: Batch) => (
                <Button
                  onClick={async () => {
                    try {
                      setResult(await get<Batch>("/imports/" + b.id));
                    } catch (e) {
                      setError((e as Error).message);
                    }
                  }}
                >
                  查看错误行
                </Button>
              ),
            },
          ]}
        />
      </Card>
      <Modal
        title={edit?.id ? "编辑数据源" : "新增数据源"}
        open={!!edit}
        onCancel={() => setEdit(undefined)}
        onOk={() => void save()}
        width={700}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="名称" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="type" label="类型">
            <Select
              disabled={!!edit?.id}
              options={[{ value: "HTTP" }, { value: "FILE" }]}
            />
          </Form.Item>
          <Form.Item name="endpoint" label="URL（HTTP 必填）" hidden={!isHttp}>
            <Input placeholder="http://127.0.0.1:4010/api/acceptance" />
          </Form.Item>
          <Space style={{ display: isHttp ? undefined : "none" }}>
            <Form.Item name="requestMethod" label="请求方法">
              <Select
                style={{ width: 120 }}
                options={[{ value: "GET" }, { value: "POST" }]}
              />
            </Form.Item>
            <Form.Item name="timeoutMs" label="超时毫秒">
              <InputNumber min={100} max={30000} />
            </Form.Item>
            <Form.Item name="refreshInterval" label="刷新分钟">
              <InputNumber min={1} max={1440} />
            </Form.Item>
            <Form.Item name="enabled" label="自动同步" valuePropName="checked">
              <Switch />
            </Form.Item>
          </Space>
          <Form.Item
            name="mappingText"
            label={
              '字段映射：目标字段 → 来源字段，例如 {"sourceRecordId":"id"}'
            }
          >
            <Input.TextArea rows={7} />
          </Form.Item>
        </Form>
      </Modal>
      <Modal
        title={`批次 ${result?.id} 结果`}
        open={!!result}
        onCancel={() => setResult(undefined)}
        footer={<Button onClick={() => setResult(undefined)}>关闭</Button>}
        width={720}
      >
        {result && (
          <>
            <p>
              成功 {result.success_count}（更新 {result.updated_count}） · 重复{" "}
              {result.duplicate_count} · 失败 {result.failure_count}
            </p>
            <Table
              rowKey="line"
              dataSource={result.errors}
              columns={[
                { title: "数据行号", dataIndex: "line" },
                { title: "原因", dataIndex: "reason" },
              ]}
            />
          </>
        )}
      </Modal>
    </>
  );
}
