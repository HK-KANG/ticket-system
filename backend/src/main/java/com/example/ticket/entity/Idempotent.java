package com.example.ticket.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 幂等记录，对应 t_idempotent。
 *
 * 解决什么问题：客户端网络抖动重复提交，同一个 requestId 会打到两次。
 * 靠 biz_key 的唯一索引兜底——第二次插入必然冲突，捕获后返回首次的结果快照，
 * 业务逻辑不会执行第二遍。
 *
 * 为什么不能只用 Redis：Redis 的"先查后写"不是原子操作，两个线程可能同时判断"不存在"，
 * 只有数据库唯一索引能真正保证唯一。
 */
@Data
@TableName("t_idempotent")
public class Idempotent {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 幂等键：bizType + requestId，唯一索引 */
    private String bizKey;

    /** 业务类型，如 CREATE_TICKET */
    private String bizType;

    /** 首次执行结果快照（JSON），重复请求直接返回它 */
    private String resultSnapshot;

    /** 过期时间，定时任务清理 */
    private LocalDateTime expireTime;

    /** 创建时间 */
    private LocalDateTime createTime;
}
