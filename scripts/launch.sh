#!/bin/sh
# Finder-friendly entry point; lifecycle and health checks remain in dev.py.
set -eu
cd "$(dirname "$0")/.."
case "${1:-}" in
  ""|--open) ;;
  *) echo "用法：scripts/launch.sh [--open]" >&2; exit 2 ;;
esac

# Finder's Terminal may not inherit Homebrew's PATH. Only this process is changed.
for prefix in /usr/local /opt/homebrew; do
  if [ -x "$prefix/bin/brew" ]; then
    PATH="$prefix/opt/openjdk@17/bin:$prefix/opt/mysql@8.0/bin:$prefix/bin:$PATH"
  fi
done
if [ -n "${JAVA_HOME:-}" ]; then PATH="$JAVA_HOME/bin:$PATH"; fi
export PATH

echo "正在启动 SCM 工作台……"
if ! ./scripts/start.sh; then
  echo "启动失败。请查看上方错误和项目 .runtime/ 日志；未结束其他项目进程。" >&2
  exit 1
fi
./scripts/status.sh
url=$(python3 -c 'import json; from pathlib import Path; s=json.loads(Path(".runtime/services.json").read_text()); print("http://127.0.0.1:"+str(s["frontend"]["port"]))')
echo "工作台已就绪：$url"
echo "停止项目服务：$(pwd)/scripts/stop.sh（保留共享 MySQL）"
if [ "${1:-}" = --open ]; then
  /usr/bin/open "$url"
  echo "已请求默认浏览器打开工作台。"
fi
