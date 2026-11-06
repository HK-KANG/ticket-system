#!/usr/bin/env bash
# 工单系统 · 中间件体检
# 用法：./scripts/verify-env.sh
set -uo pipefail
cd "$(dirname "$0")/.." || exit 1

ENV_FILE=".env"
if [ ! -f "$ENV_FILE" ]; then echo "缺少 .env"; exit 1; fi
MYSQL_PW=$(grep '^MYSQL_ROOT_PASSWORD=' "$ENV_FILE" | cut -d= -f2-)
DC="docker compose -f docker/docker-compose.yml --env-file .env"

echo "==== 1. 容器状态 ===="
$DC ps --format 'table {{.Name}}\t{{.Status}}' 2>/dev/null

echo
echo "==== 2. MySQL ===="
if docker exec ticket-mysql sh -c "MYSQL_PWD='$MYSQL_PW' mysql -uroot -N -e 'select version(), @@innodb_buffer_pool_size/1024/1024' " 2>/dev/null | sed 's/^/  version\/buffer(MB): /'; then :; else echo "  FAIL"; fi
docker exec ticket-mysql sh -c "MYSQL_PWD='$MYSQL_PW' mysql -uroot -N -e 'show databases' " 2>/dev/null | sed 's/^/  db: /'

echo
echo "==== 3. Redis ===="
echo -n "  ping: "; docker exec ticket-redis redis-cli ping 2>/dev/null || echo FAIL
echo -n "  maxmemory: "; docker exec ticket-redis redis-cli config get maxmemory 2>/dev/null | tail -1

echo
echo "==== 4. RabbitMQ ===="
echo -n "  ping: "; docker exec ticket-rabbitmq rabbitmq-diagnostics -q ping 2>/dev/null || echo FAIL
echo -n "  版本: "; docker exec ticket-rabbitmq rabbitmq-diagnostics -q server_version 2>/dev/null || echo FAIL

echo
echo "==== 5. 内存占用 ===="
docker stats --no-stream --format '  {{.Name}}\t{{.MemUsage}}\t{{.CPUPerc}}' 2>/dev/null

echo
echo "==== 6. 从 Windows 侧看端口 ===="
for p in 3306 6379 5672 15672; do
  if timeout 2 bash -c "</dev/tcp/127.0.0.1/$p" 2>/dev/null; then echo "  127.0.0.1:$p 可连"; else echo "  127.0.0.1:$p 不通"; fi
done
