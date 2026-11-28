#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
HOST="${DEPLOY_SSH_HOST:-vps}"
SERVICE="weituolaoshi"
APP_DIR="/opt/weituolaoshi"
PORT="18083"
ALLOW_DIRTY=0
SKIP_TESTS=0

usage() {
  cat <<'EOF'
用法：deploy/update-server.sh [--allow-dirty] [--skip-tests]

默认要求 Git 工作区干净，构建后台和服务端，运行测试，然后部署到 SSH 主机 vps。
--allow-dirty  明确允许发布当前未提交的改动
--skip-tests   跳过 Maven 测试（仍编译、打包）
可用 DEPLOY_SSH_HOST 覆盖 SSH 主机别名。
EOF
}

for arg in "$@"; do
  case "$arg" in
    --allow-dirty) ALLOW_DIRTY=1 ;;
    --skip-tests) SKIP_TESTS=1 ;;
    -h|--help) usage; exit 0 ;;
    *) usage >&2; exit 2 ;;
  esac
done

cd "$ROOT"
if (( ! ALLOW_DIRTY )) && [[ -n "$(git status --porcelain)" ]]; then
  echo '工作区有未提交改动；请先提交，或显式使用 --allow-dirty。' >&2
  exit 1
fi

command -v npm >/dev/null
command -v ssh >/dev/null
command -v scp >/dev/null
command -v shasum >/dev/null

echo '构建管理后台…'
npm ci --prefix admin
npm run build --prefix admin

echo '构建服务端…'
if (( SKIP_TESTS )); then
  ./mvnw -f server/pom.xml clean package -DskipTests
else
  ./mvnw -f server/pom.xml clean package
fi

JAR="$ROOT/server/target/server-0.1.0.jar"
[[ -s "$JAR" ]] || { echo "未找到构建产物：$JAR" >&2; exit 1; }
SHA="$(shasum -a 256 "$JAR" | awk '{print $1}')"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
REMOTE_JAR="/tmp/weituolaoshi-${STAMP}-${SHA:0:12}.jar"
BACKUP="$APP_DIR/backups/update-${STAMP}"

echo "上传服务端 JAR（SHA-256 $SHA）…"
scp "$JAR" "$HOST:$REMOTE_JAR"
REMOTE_SHA="$(ssh "$HOST" "sha256sum '$REMOTE_JAR' | cut -d ' ' -f 1")"
[[ "$SHA" == "$REMOTE_SHA" ]] || { echo '上传文件校验失败。' >&2; exit 1; }

echo "停服并备份到 $BACKUP …"
ssh "$HOST" "set -eu
sudo systemctl stop '$SERVICE'
if ! { sudo mkdir -p '$BACKUP' && sudo cp '$APP_DIR/server.jar' '$BACKUP/server.jar' && sudo cp -a '$APP_DIR/data' '$BACKUP/data'; }; then
  sudo systemctl start '$SERVICE'
  echo '备份失败，已尝试恢复旧服务。' >&2
  exit 1
fi
sudo install -m 0644 '$REMOTE_JAR' '$APP_DIR/server.jar'
sudo systemctl start '$SERVICE'
sudo systemctl is-active --quiet '$SERVICE'"

echo '等待服务启动并检查接口…'
for attempt in {1..30}; do
  if ssh "$HOST" "curl -fsS --max-time 5 'http://127.0.0.1:$PORT/v1/services' >/dev/null && curl -fsS --max-time 5 'http://127.0.0.1:$PORT/admin/index.html' >/dev/null"; then
    break
  fi
  if (( attempt == 30 )); then
    echo "服务检查失败。备份：$BACKUP；查看日志：ssh $HOST 'sudo journalctl -u $SERVICE -n 100 --no-pager'" >&2
    exit 1
  fi
  sleep 2
done

LIVE_SHA="$(ssh "$HOST" "sudo sha256sum '$APP_DIR/server.jar' | cut -d ' ' -f 1")"
[[ "$SHA" == "$LIVE_SHA" ]] || { echo '线上 JAR 校验失败。' >&2; exit 1; }
echo "发布完成。SHA-256：$SHA"
echo "备份：$BACKUP"
