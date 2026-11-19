import { useEffect, useState, lazy, Suspense } from "react";
import { NavLink, Route, Routes } from "react-router-dom";
import { Alert, Button, Spin, Tag } from "antd";
import { useConfig } from "./state/config";
import { iconGlyph, navigationTree } from "./state/workbenchPolicy";
import AiPanel from "./components/AiPanel";
const Dashboard = lazy(() => import("./pages/Dashboard"));
const DataSources = lazy(() => import("./pages/DataSources"));
const Settings = lazy(() => import("./pages/Settings"));
const Logs = lazy(() => import("./pages/Logs"));
export default function App() {
  const { config, error, load } = useConfig();
  const [assistantOpen, setAssistantOpen] = useState<boolean>();
  const [prompt, setPrompt] = useState<{ text: string }>();
  const open = assistantOpen ?? config?.aiExpanded ?? false;
  function showAssistant() {
    setAssistantOpen(true);
    if (window.matchMedia("(max-width: 900px)").matches) {
      requestAnimationFrame(() =>
        document.getElementById("ai-assistant")?.scrollIntoView(),
      );
    }
  }
  useEffect(() => setAssistantOpen(undefined), [config?.aiExpanded]);
  useEffect(() => {
    void load();
  }, [load]);
  if (error)
    return (
      <Alert
        type="error"
        message={error}
        action={<Button onClick={() => void load()}>重试</Button>}
      />
    );
  if (!config) return <Spin fullscreen />;
  return (
    <div className={"shell" + (open ? "" : " ai-collapsed")}>
      <aside className="sidebar">
        <div className="brand">
          <span>S</span>
          <div>
            SCM <b>WORKBENCH</b>
            <small>供应链智能工作台</small>
          </div>
        </div>
        <div className="nav-caption">业务空间</div>
        <nav>
          {navigationTree(config.navigation).map((n) =>
            n.path?.startsWith("/") ? (
              <NavLink
                style={{ paddingLeft: 14 + n.depth * 14 }}
                key={n.key}
                to={n.path}
                end
              >
                <span>{iconGlyph(n.icon)}</span>
                {n.name}
              </NavLink>
            ) : (
              <a
                style={{ paddingLeft: 14 + n.depth * 14 }}
                key={n.key}
                href={n.path}
                target="_blank"
                rel="noreferrer"
              >
                <span>{iconGlyph(n.icon)}</span> {n.name}
              </a>
            ),
          )}
        </nav>
        <div className="sidebar-footer">
          当前用户：演示用户
          <small>
            <NavLink to="/settings">配置入口</NavLink>
          </small>
        </div>
      </aside>
      <div className="workspace">
        <header>
          <span>统一 SCM 业务工作台</span>
          <Tag color="blue">演示环境</Tag>
          <Button
            size="small"
            aria-expanded={open}
            onClick={() => (open ? setAssistantOpen(false) : showAssistant())}
          >
            {open ? "收起助手" : "打开 AI 助手"}
          </Button>
        </header>
        <main>
          <Suspense fallback={<Spin />}>
            <Routes>
              <Route
                path="/"
                element={
                  <Dashboard
                    onPrompt={(text) => {
                      setPrompt({ text });
                      showAssistant();
                    }}
                  />
                }
              />
              {/* TODO：这里通过 businessType 参数传入业务差异，共同能力集中实现。 */}
              <Route
                path="/procurement"
                element={<Dashboard businessType="PROCUREMENT" />}
              />
              <Route
                path="/inventory"
                element={<Dashboard businessType="INVENTORY" />}
              />
              <Route
                path="/suppliers"
                element={<Dashboard businessType="SUPPLIER" />}
              />
              <Route path="/analytics" element={<Dashboard analytics />} />
              <Route path="/data-sources" element={<DataSources />} />
              <Route path="/settings" element={<Settings />} />
              <Route path="/logs" element={<Logs />} />
              <Route
                path="*"
                element={<Alert type="warning" message="页面不存在" />}
              />
            </Routes>
          </Suspense>
        </main>
      </div>
      <AiPanel open={open} prompt={prompt} />
    </div>
  );
}
