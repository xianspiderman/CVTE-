import { answerText } from "../state/aiPresentation";
import { taskTitle } from "../state/presentation";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { Alert, Button, Input, Space, Spin, Tag } from "antd";
import { chat, type AiReply, type AiStatus, type Proposal } from "../api/ai";
import { get, post, type Page } from "../api/business";
import { ApiError } from "../api/client";
import { useConfig } from "../state/config";
import AiEvidence from "./AiEvidence";

const questions = [
  "我今天有多少条待办？",
  "哪些供应商还没有回复？",
  "最近 7 天的异常趋势如何？",
];
const toolLabels: Record<string, string> = {
  get_todo_summary: "待办汇总",
  search_todos: "待办查询",
  get_metric: "查询指标",
  get_system_link: "生成链接",
  propose_workbench_config: "生成配置提案",
  generate_business_summary: "生成业务摘要",
};
interface Turn {
  question: string;
  reply?: AiReply;
  error?: string;
  decision?: string;
}
export default function AiPanel({
  open,
  prompt,
}: {
  open: boolean;
  prompt: { text: string } | undefined;
}) {
  const loadConfig = useConfig((s) => s.load);
  const [status, setStatus] = useState<AiStatus>();
  const [summary, setSummary] = useState<Record<string, number>>();
  const [risk, setRisk] = useState<Page>();
  const [turns, setTurns] = useState<Turn[]>([]);
  const [session, setSession] = useState<string>();
  const [pending, setPending] = useState<Proposal>();
  const [input, setInput] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [serviceError, setServiceError] = useState("");
  const transcript = useRef<HTMLDivElement>(null);
  const sending = useRef(false);
  const canSend = status?.configured && !pending && !busy;
  async function readStatus() {
    try {
      setStatus(await get<AiStatus>("/ai/status"));
      setServiceError("");
    } catch (e) {
      setStatus(undefined);
      setServiceError((e as Error).message);
    }
  }
  useEffect(() => {
    if (!open) return;
    void readStatus();
    get<Record<string, number>>("/metrics/summary", { owner: "演示用户" })
      .then(setSummary)
      .catch(() => setSummary(undefined));
    get<Page>("/todos", { view: "HIGH_RISK", owner: "演示用户", size: 3 })
      .then(setRisk)
      .catch(() => setRisk(undefined));
  }, [open]);
  useEffect(() => {
    if (prompt) setInput(prompt.text);
  }, [prompt]);
  useEffect(() => {
    if (open && transcript.current)
      transcript.current.scrollTop = transcript.current.scrollHeight;
  }, [turns, open, busy]);

  async function send(text: string, retry = false) {
    const question = text.trim();
    if (!question || question.length > 2000 || !canSend || sending.current)
      return;
    sending.current = true;
    setBusy(true);
    setError("");
    setInput("");
    const turn = { question };
    setTurns((history) => [...(retry ? history.slice(0, -1) : history), turn]);
    try {
      const reply = await chat(session, question);
      setSession(reply.sessionId);
      setPending(reply.pending ?? undefined);
      setTurns((history) => [...history.slice(0, -1), { question, reply }]);
    } catch (e) {
      const expired = e instanceof ApiError && e.status === 410;
      if (expired) setSession(undefined);
      setTurns((history) => [
        ...history.slice(0, -1),
        { question, error: (e as Error).message },
      ]);
    } finally {
      sending.current = false;
      setBusy(false);
    }
  }

  async function decide(confirm: boolean) {
    if (!pending || !session || sending.current) return;
    sending.current = true;
    setBusy(true);
    setError("");
    try {
      const result = await post<{ message: string; applied: boolean }>(
        "/ai/config/decision",
        {
          sessionId: session,
          proposalId: pending.id,
          confirm,
        },
      );
      setPending(undefined);
      setTurns((history) =>
        history.map((t) =>
          t.reply?.pending?.id === pending.id
            ? { ...t, decision: result.message }
            : t,
        ),
      );
      if (result.applied) await loadConfig();
    } catch (e) {
      const message = (e as Error).message;
      if (
        e instanceof ApiError &&
        ([404, 410].includes(e.status) || message.includes("配置已变化"))
      ) {
        setPending(undefined);
        setTurns((history) =>
          history.map((t) =>
            t.reply?.pending?.id === pending.id
              ? { ...t, decision: message }
              : t,
          ),
        );
        if (e.status === 410) setSession(undefined);
      } else setError(message);
    } finally {
      sending.current = false;
      setBusy(false);
    }
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    void send(input);
  }

  return (
    <aside
      id="ai-assistant"
      className="assistant"
      hidden={!open}
      aria-label="AI 助手"
    >
      <div className="ai-title">
        <h2>AI 助手</h2>
        <Tag color={status?.configured ? "blue" : undefined}>
          {serviceError
            ? "状态读取失败"
            : status
              ? status.configured
                ? status.model
                : "未配置"
              : "检查中"}
        </Tag>
      </div>
      {serviceError && (
        <Alert
          type="error"
          message={serviceError}
          action={
            <Button size="small" onClick={() => void readStatus()}>
              重试
            </Button>
          }
        />
      )}
      {status && !status.configured && (
        <Alert type="info" message="配置模型服务后可开始对话。" />
      )}
      <details className="ai-intro" open>
        <summary>待办摘要与推荐关注</summary>
        <p>
          {summary
            ? `我的待办 ${summary.TOTAL} 条 · 逾期 ${summary.OVERDUE} 条 · 今日到期 ${summary.TODAY_DUE} 条`
            : "暂未取得待办摘要"}
        </p>
        {risk?.items.map((item) => (
          <small key={item.id}>高风险 · {taskTitle(item.title)}</small>
        ))}
        {risk?.total === 0 && <small>暂无高风险待办</small>}
      </details>
      <Space wrap className="ai-questions">
        {questions.map((question) => (
          <Button
            size="small"
            disabled={!canSend}
            key={question}
            onClick={() => void send(question)}
          >
            {question}
          </Button>
        ))}
      </Space>
      <div className="ai-history-title">
        <h3>对话历史</h3>
        <Button
          type="link"
          size="small"
          disabled={busy || !!pending || !turns.length}
          onClick={() => {
            setSession(undefined);
            setTurns([]);
            setError("");
            setInput("");
          }}
        >
          新对话
        </Button>
      </div>
      <div
        className="ai-history"
        ref={transcript}
        role="log"
        aria-label="AI 对话"
      >
        {turns.length === 0 && <p className="muted">暂无对话</p>}
        {turns.map((turn, index) => (
          <article className="ai-turn" key={index}>
            <p className="ai-user">{turn.question}</p>
            {turn.error && (
              <Alert
                type="error"
                message={turn.error}
                action={
                  index === turns.length - 1 && (
                    <Button
                      size="small"
                      disabled={!canSend}
                      onClick={() => void send(turn.question, true)}
                    >
                      重试
                    </Button>
                  )
                }
              />
            )}
            {turn.reply && (
              <>
                <p className="ai-answer">{answerText(turn.reply)}</p>
                {turn.reply.tools.map((tool, i) => (
                  <Tag
                    key={i}
                    color={tool.status === "FAILED" ? "red" : "geekblue"}
                  >
                    {toolLabels[tool.name] || tool.name} ·{" "}
                    {tool.status === "FAILED"
                      ? "失败"
                      : tool.status === "SUCCESS"
                        ? "完成"
                        : "待确认"}
                  </Tag>
                ))}
                {turn.reply.tools
                  .filter((tool) => tool.message)
                  .map((tool, i) => (
                    <small className="ai-tool-error" key={i}>
                      {tool.message}
                    </small>
                  ))}
                {turn.reply.evidence.map((evidence, i) => (
                  <AiEvidence key={i} evidence={evidence} />
                ))}
                {turn.reply.pending && (
                  <section className="ai-evidence" aria-label="配置修改提案">
                    <strong>配置修改提案</strong>
                    {turn.reply.pending.changes.map((change) => (
                      <p key={change.field}>
                        {change.item} · {change.field} · {change.before} →{" "}
                        {change.after}
                      </p>
                    ))}
                    {pending?.id === turn.reply.pending.id && (
                      <Space wrap>
                        <Button
                          type="primary"
                          disabled={busy}
                          onClick={() => void decide(true)}
                        >
                          确认修改
                        </Button>
                        <Button
                          disabled={busy}
                          onClick={() => void decide(false)}
                        >
                          取消
                        </Button>
                      </Space>
                    )}
                    {turn.decision && <p role="status">{turn.decision}</p>}
                  </section>
                )}
              </>
            )}
          </article>
        ))}
        {busy && (
          <div role="status">
            <Spin size="small" />{" "}
            {pending ? "处理配置提案中" : "正在查询并生成回复"}
          </div>
        )}
      </div>
      {error && <Alert showIcon type="error" message={error} />}
      <form className="composer" onSubmit={submit}>
        <Input.TextArea
          aria-label="AI 消息"
          disabled={!canSend}
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onPressEnter={(e) => {
            if (e.shiftKey || e.nativeEvent.isComposing) return;
            e.preventDefault();
            void send(input);
          }}
          maxLength={2000}
          rows={2}
          placeholder={
            pending ? "请先确认或取消配置提案" : "询问待办、指标或配置"
          }
        />
        <Button
          type="primary"
          htmlType="submit"
          disabled={!canSend || !input.trim()}
          block
        >
          发送
        </Button>
      </form>
    </aside>
  );
}
