#!/usr/bin/env bash
# Phase 1 验收：核对表与索引
set -uo pipefail
PW=$(grep '^MYSQL_ROOT_PASSWORD=' /mnt/d/projects/ticket-system/.env | cut -d= -f2-)
Q() { docker exec ticket-mysql sh -c "MYSQL_PWD='$PW' mysql -uroot -N -B ticket -e \"$1\"" 2>/dev/null; }

echo "=== 1. 表清单 ==="
Q "show tables" | sed 's/^/  /'

echo
echo "=== 2. 每张表字段数 ==="
Q "select table_name, count(*) from information_schema.columns where table_schema='ticket' group by table_name order by table_name" | sed 's/^/  /'

echo
echo "=== 3. 索引清单 ==="
Q "select table_name, index_name, group_concat(column_name order by seq_in_index) cols, non_unique from information_schema.statistics where table_schema='ticket' group by table_name, index_name, non_unique order by table_name, index_name" \
  | awk -F'\t' '{printf "  %-20s %-22s %-38s %s\n", $1, $2, $3, ($4=="0"?"UNIQUE":"")}'

echo
echo "=== 4. 引擎/字符集 ==="
Q "select table_name, engine, table_collation from information_schema.tables where table_schema='ticket' order by table_name" | sed 's/^/  /'

echo
echo "=== 5. t_ticket 关键列类型 ==="
Q "select column_name, column_type, is_nullable, column_default from information_schema.columns where table_schema='ticket' and table_name='t_ticket' order by ordinal_position" \
  | awk -F'\t' '{printf "  %-16s %-14s null=%-4s default=%s\n", $1, $2, $3, $4}'

echo
echo "=== 6. Flyway 历史 ==="
Q "select version, description, success from flyway_schema_history" | sed 's/^/  /'
