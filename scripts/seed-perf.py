#!/usr/bin/env python3
"""给 Phase 6 性能优化造测试数据：10 万工单 / 30 万日志 / 13 万回复。

设计要点：
1. 工单 id 从 10001 起，避开数据库里已有的用户自建工单（id 7/8/17/18）
2. ticket_no = TK + 创建日期 + 全局序号，天然唯一
3. 状态按真实分布：CLOSED 65% / PROCESSING 12% / PENDING 12% / ASSIGNED 5% / RESOLVED 6%
4. 每个 INSERT 2000 行，避免单条语句过大
5. 文件开头 SET sql_log_bin=0：这批数据不用做主从/恢复，不为它写 binlog
6. 固定随机种子，可复现
"""
import datetime
import random

OUT = "/tmp/seed_perf.sql"  # 可改成自己的路径
N_TICKET = 100000
TICKET_ID_BASE = 10001
BATCH = 2000

random.seed(20261109)

START = datetime.datetime(2025, 5, 9, 8, 0, 0)
END = datetime.datetime(2026, 11, 9, 12, 0, 0)
SPAN = (END - START).total_seconds()

# (分类, 权重, 技能组)
CATS = [("退款", 30, "售后一组"), ("质量问题", 25, "售后一组"),
        ("物流", 25, "售后二组"), ("发票", 10, "售后二组"), ("其他", 10, "售后二组")]
PRIOS = [("P0", 8), ("P1", 15), ("P2", 77)]
STATUS = [("CLOSED", 65), ("PROCESSING", 12), ("PENDING", 12), ("ASSIGNED", 5), ("RESOLVED", 6)]
# (分类, 优先级) -> SLA 分钟数，没有组合的就是 NULL（真实系统里规则也会缺）
SLA_MIN = {("退款", "P0"): 30, ("退款", "P2"): 240,
           ("质量问题", "P0"): 30, ("质量问题", "P2"): 240,
           ("物流", "P1"): 60, ("物流", "P2"): 480,
           ("发票", "P2"): 480, ("其他", "P2"): 480}


def pick(weighted):
    """按权重随机取第一列。weighted = [(值, 权重), ...] 或 [(值, 权重, 附加), ...]"""
    total = sum(x[1] for x in weighted)
    r = random.uniform(0, total)
    acc = 0
    for item in weighted:
        acc += item[1]
        if r <= acc:
            return item[0]
    return weighted[-1][0]


def fmt(dt):
    return "NULL" if dt is None else "'" + dt.strftime("%Y-%m-%d %H:%M:%S.%f")[:-3] + "'"


f = open(OUT, "w", encoding="utf-8")
f.write("SET sql_log_bin=0;\nSET SESSION sql_mode='NO_ENGINE_SUBSTITUTION';\n")

# ---------------- 工单 ----------------
ticket_rows, log_rows, comment_rows = [], [], []
log_id_needed = True
n_log = n_comment = 0

f.write("-- tickets\n")
for i in range(N_TICKET):
    tid = TICKET_ID_BASE + i
    cat = pick(CATS)
    grp = next(g for c, _, g in CATS if c == cat)
    prio = pick(PRIOS)
    status = pick(STATUS)
    create = START + datetime.timedelta(seconds=random.uniform(0, SPAN))
    sla = SLA_MIN.get((cat, prio))
    sla_dl = create + datetime.timedelta(minutes=sla) if sla else None

    handler = None if status == "PENDING" else random.choice([3, 4])
    accept = None if status == "PENDING" else create + datetime.timedelta(minutes=random.randint(1, 45))
    finish = None
    if status in ("CLOSED", "RESOLVED") and accept:
        finish = accept + datetime.timedelta(minutes=random.randint(5, 600))

    no = "TK" + create.strftime("%Y%m%d") + f"{i + 1:06d}"
    title = f"{cat}问题 - 订单A{10000 + i}"
    content = (f"客户反馈：订单 A{10000 + i} 出现{cat}相关问题，"
               f"已提供凭证，希望尽快处理。补充说明：客户在留言中提到此前已联系过一次。")

    ticket_rows.append(
        f"({tid},'{no}','{title}','{content}','{cat}','{prio}','{status}',"
        f"{random.choice([1, 2])},{'NULL' if handler is None else handler},'{grp}',0,"
        f"{fmt(sla_dl)},{fmt(accept)},{fmt(finish)},0,{fmt(create)},{fmt(create)},0)")
    n_log += 0

    # 3 条流转日志：CREATE → ACCEPT → CLOSE
    op_create = random.choice([1, 2])
    log_rows.append(f"({tid},NULL,'PENDING',{op_create},'CREATE','提交工单',{fmt(create)})")
    n_log += 1
    if accept:
        log_rows.append(f"({tid},'PENDING','PROCESSING',{handler},'ACCEPT','抢单成功',{fmt(accept)})")
        n_log += 1
    if finish:
        log_rows.append(f"({tid},'PROCESSING','CLOSED',{handler},'CLOSE','处理完成，客户确认',{fmt(finish)})")
        n_log += 1

    # 回复：30% 的工单 2 条，其余 1 条
    n_c = 2 if i % 10 < 3 else 1
    for k in range(n_c):
        uid = random.choice([1, 2]) if k % 2 == 0 else (handler or 3)
        txt = "请问处理到哪一步了？比较着急。" if k % 2 == 0 else "已为您提交处理，请耐心等待。"
        internal = 1 if (k == 1 and i % 7 == 0) else 0
        ctime = (accept or create) + datetime.timedelta(minutes=random.randint(1, 120))
        comment_rows.append(f"({tid},{uid},'{txt}',{internal},{fmt(ctime)})")
        n_comment += 1

    if len(ticket_rows) >= BATCH:
        f.write("INSERT INTO t_ticket (id,ticket_no,title,content,category,priority,status,creator_id,"
                "handler_id,skill_group,version,sla_deadline,accept_time,finish_time,escalate_flag,"
                "create_time,update_time,deleted) VALUES\n" + ",\n".join(ticket_rows) + ";\n")
        ticket_rows = []
    if len(log_rows) >= BATCH:
        f.write("INSERT INTO t_ticket_log (ticket_id,from_status,to_status,operator_id,action,remark,create_time) VALUES\n"
                + ",\n".join(log_rows) + ";\n")
        log_rows = []
    if len(comment_rows) >= BATCH:
        f.write("INSERT INTO t_ticket_comment (ticket_id,user_id,content,is_internal,create_time) VALUES\n"
                + ",\n".join(comment_rows) + ";\n")
        comment_rows = []

for name, rows, cols in (
    ("t_ticket", ticket_rows, None),
    ("t_ticket_log", log_rows, None),
    ("t_ticket_comment", comment_rows, None),
):
    if rows:
        if name == "t_ticket":
            f.write("INSERT INTO t_ticket (id,ticket_no,title,content,category,priority,status,creator_id,"
                    "handler_id,skill_group,version,sla_deadline,accept_time,finish_time,escalate_flag,"
                    "create_time,update_time,deleted) VALUES\n" + ",\n".join(rows) + ";\n")
        elif name == "t_ticket_log":
            f.write("INSERT INTO t_ticket_log (ticket_id,from_status,to_status,operator_id,action,remark,create_time) VALUES\n"
                    + ",\n".join(rows) + ";\n")
        else:
            f.write("INSERT INTO t_ticket_comment (ticket_id,user_id,content,is_internal,create_time) VALUES\n"
                    + ",\n".join(rows) + ";\n")

f.write("SET sql_log_bin=1;\n")
f.close()

print(f"tickets={N_TICKET} logs={n_log} comments={n_comment}")
import os
print(f"file={OUT} size={os.path.getsize(OUT)/1024/1024:.1f} MB")
