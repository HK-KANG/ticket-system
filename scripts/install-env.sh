#!/usr/bin/env bash
# 工单系统 · WSL 环境安装（混合模式专用）
# 只装 Docker 和命令行客户端；JDK/Maven 已经在 Windows 侧装好，这里不需要
# 用法：cd /mnt/d/projects/ticket-system && ./scripts/install-env.sh
set -uo pipefail

log() { echo -e "\n==== $* ===="; }

log "0/6 环境检查"
. /etc/os-release
echo "系统：$PRETTY_NAME"
free -h | head -2
echo "WSL 发行版：${WSL_DISTRO_NAME:-未知}"

log "1/6 更新包索引（会要 sudo 密码）"
sudo apt-get update -qq || { echo "apt update 失败，检查网络"; exit 1; }

log "2/6 安装 Docker + 客户端工具"
sudo apt-get install -y \
  docker.io \
  docker-compose-v2 \
  mysql-client \
  redis-tools \
  wrk \
  jq \
  || { echo "安装失败，看上面的报错"; exit 1; }

log "3/6 当前用户加入 docker 组（免 sudo 用 docker）"
sudo usermod -aG docker "$USER" || true

log "4/6 启动 Docker 服务"
if pidof systemd >/dev/null 2>&1; then
  echo "检测到 systemd，用 systemctl 启动"
  sudo systemctl start docker && sudo systemctl enable docker
else
  echo "无 systemd，用 service 启动"
  sudo service docker start
fi
sleep 3

log "5/6 检查 Docker 能否运行容器"
if sudo docker info >/dev/null 2>&1; then
  echo "Docker 守护进程正常"
else
  echo "警告：Docker 启动失败，下面这些信息有助于排查："
  sudo service docker status 2>&1 | head -10
  sudo tail -20 /var/log/docker.log 2>/dev/null
fi

log "6/6 版本验证"
echo "--- Docker ---";        docker --version 2>/dev/null || echo "需要 newgrp docker 或重开终端"
echo "--- Compose ---";       docker compose version 2>/dev/null || echo "compose 插件未就绪"
echo "--- wrk ---";           wrk --version 2>&1 | head -1
echo "--- mysql 客户端 ---";  mysql --version 2>/dev/null
echo "--- redis-cli ---";     redis-cli --version 2>/dev/null

cat <<'EOF'

============================================
装完了。三件事注意：

1) docker 组权限要重新登录才生效：
   关掉这个终端重开，或执行 newgrp docker

2) 每次重启 WSL 后 Docker 不会自动启动（没有 systemd 的话）：
   sudo service docker start

3) 中间件容器还没起，那是下一步。
   镜像也还没下载（约 900 MB）。
============================================
EOF
