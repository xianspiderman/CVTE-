#!/usr/bin/env python3
"""Local lifecycle manager. Credentials stay in environment; logs under .runtime."""
import fcntl, json, os, re, signal, socket, subprocess, sys, time, urllib.request, shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RUN = ROOT / ".runtime"
RUN.mkdir(exist_ok=True)
os.chdir(ROOT)
lock = open(RUN / "lifecycle.lock", "w")
fcntl.flock(lock, fcntl.LOCK_EX)


def load_env():
    if not (ROOT / ".env").exists():
        raise RuntimeError("缺少 .env：参考 .env.example 配置项目专用数据库账号")
    for line in (ROOT / ".env").read_text().splitlines():
        if line.strip() and not line.startswith("#"):
            k, v = line.split("=", 1)
            os.environ.setdefault(k, v)
    for key in ["DB_USERNAME", "DB_PASSWORD"]:
        if not os.environ.get(key):
            raise RuntimeError("缺少环境变量 " + key)


def command(args, **kw):
    return subprocess.run(args, check=True, **kw)


def mysql(sql):
    env = {**os.environ, "MYSQL_PWD": os.environ["DB_PASSWORD"]}
    return command(
        [
            "mysql",
            "--protocol=TCP",
            "-h",
            os.getenv("DB_HOST", "127.0.0.1"),
            "-P",
            os.getenv("DB_PORT", "3306"),
            "-u",
            os.environ["DB_USERNAME"],
            os.getenv("DB_NAME", "scm_workbench"),
        ],
        input=sql,
        text=True,
        env=env,
        capture_output=True,
    )


def migrate():
    mysql((ROOT / "database/init.sql").read_text())
    for path in sorted((ROOT / "database/migrations").glob("*.sql")):
        for statement in path.read_text().split(";"):
            if not statement.strip():
                continue
            try:
                mysql(statement + ";")
            except subprocess.CalledProcessError as e:
                if "ERROR 1060" not in e.stderr:
                    raise


def busy(port):
    with socket.socket() as s:
        return s.connect_ex(("127.0.0.1", port)) == 0


def owned(info):
    p = subprocess.run(
        ["ps", "-p", str(info["pid"]), "-o", "command="], capture_output=True, text=True
    )
    return p.returncode == 0 and info["marker"] in p.stdout


STATE = RUN / "services.json"


def state():
    return json.loads(STATE.read_text()) if STATE.exists() else {}


def stop():
    for name, info in reversed(list(state().items())):
        if owned(info):
            os.killpg(info["pid"], signal.SIGTERM)
            for _ in range(50):
                if not owned(info):
                    break
                time.sleep(0.1)
            if owned(info):
                os.killpg(info["pid"], signal.SIGKILL)
            print(name + " stopped")
    STATE.unlink(missing_ok=True)
    # MySQL is a shared pre-existing Homebrew service: never stop it automatically.


def start():
    existing = state()
    if set(existing) == {"mock", "backend", "frontend"} and all(
        owned(x) for x in existing.values()
    ):

        for info in existing.values():
            with urllib.request.urlopen(
                f"http://127.0.0.1:{info['port']}{info['path']}", timeout=5
            ) as r:
                if r.status != 200:
                    raise RuntimeError("已启动服务健康检查失败")
        print("服务已启动且健康；未重复创建进程")
        return
    if any(owned(x) for x in existing.values()):
        raise RuntimeError(
            "存在部分运行的服务；先执行 scripts/stop.sh，日志在 .runtime/"
        )
    load_env()
    ports = {
        n: int(os.getenv(k, str(d)))
        for n, k, d in [
            ("mock", "MOCK_PORT", 4010),
            ("backend", "BACKEND_PORT", 8080),
            ("frontend", "FRONTEND_PORT", 5173),
        ]
    }
    if len(set(ports.values())) != 3:
        raise RuntimeError("服务端口不能重复")
    for n, p in ports.items():
        if busy(p):
            raise RuntimeError(f"{n} 端口 {p} 被占用；未结束任何已有进程")
    if os.getenv("DB_HOST", "127.0.0.1") not in ("127.0.0.1", "localhost"):
        raise RuntimeError("仅支持本地数据库")
    if not busy(int(os.getenv("DB_PORT", "3306"))):
        command(["brew", "services", "start", "mysql@8.0"])
    for i in range(30):
        try:
            mysql("SELECT 1;")
            break
        except subprocess.CalledProcessError:
            if i == 29:
                raise RuntimeError("数据库连接失败，检查 .env 账号、库名及 MySQL 状态")
            time.sleep(1)
    migrate()
    with open(RUN / "build.log", "w") as log:
        print("构建后端与前端；日志 .runtime/build.log", flush=True)
        command(
            ["mvn", "-B", "-f", "backend/pom.xml", "package"], stdout=log, stderr=log
        )
        command(["npm", "ci"], cwd=ROOT / "frontend", stdout=log, stderr=log)
        command(["npm", "run", "build"], cwd=ROOT / "frontend", stdout=log, stderr=log)
    shutil.copy2(
        ROOT / "backend/target/scm-workbench-0.1.0-SNAPSHOT.jar",
        RUN / "scm-workbench.jar",
    )
    specs = [
        (
            "mock",
            ["node", str(ROOT / "mock-service/server.mjs")],
            str(ROOT / "mock-service/server.mjs"),
            "/health",
        ),
        (
            "backend",
            ["java", "-jar", str(RUN / "scm-workbench.jar")],
            str(RUN / "scm-workbench.jar"),
            "/actuator/health",
        ),
        (
            "frontend",
            ["node", str(ROOT / "frontend/node_modules/vite/bin/vite.js")],
            str(ROOT / "frontend/node_modules/vite/bin/vite.js"),
            "/",
        ),
    ]
    current = {}
    try:
        for name, args, marker, path in specs:
            with open(RUN / (name + ".log"), "a") as log:
                p = subprocess.Popen(
                    args,
                    cwd=ROOT / "frontend" if name == "frontend" else ROOT,
                    stdin=subprocess.DEVNULL,
                    stdout=log,
                    stderr=log,
                    start_new_session=True,
                )
            current[name] = {
                "pid": p.pid,
                "marker": marker,
                "port": ports[name],
                "path": path,
            }
            STATE.write_text(json.dumps(current))
            for _ in range(90):
                if p.poll() is not None:
                    raise RuntimeError(
                        name + " 异常退出，检查 .runtime/" + name + ".log"
                    )
                try:
                    with urllib.request.urlopen(
                        f"http://127.0.0.1:{ports[name]}{path}", timeout=1
                    ) as r:
                        if r.status == 200:
                            break
                except Exception:
                    pass
                time.sleep(1)
            else:
                raise RuntimeError(name + " 健康检查超时")
            print(f"{name} ready: http://127.0.0.1:{ports[name]}", flush=True)
    except BaseException:
        stop()
        raise


try:
    action = sys.argv[1] if len(sys.argv) > 1 else "status"
    if action == "start":
        start()
    elif action == "stop":
        stop()
    elif action == "init":
        load_env()
        migrate()
        print("初始化完成")
    elif action == "status":
        current = state()
        if set(current) != {"mock", "backend", "frontend"}:
            raise RuntimeError(
                "项目服务状态不完整；检查 .runtime 日志并执行 stop/start"
            )
        for name, info in current.items():
            if not owned(info):
                raise RuntimeError(name + " 进程异常，检查 .runtime/" + name + ".log")
            with urllib.request.urlopen(
                f"http://127.0.0.1:{info['port']}{info['path']}", timeout=5
            ) as r:
                print(name, r.status)
    else:
        raise RuntimeError("未知命令")
except Exception as e:
    # Do not echo subprocess SQL/environment containing credentials.
    print(
        "ERROR: "
        + (
            str(e)
            if not isinstance(e, subprocess.CalledProcessError)
            else "命令执行失败；检查 .runtime/build.log 或本地数据库权限"
        ),
        file=sys.stderr,
    )
    sys.exit(1)
