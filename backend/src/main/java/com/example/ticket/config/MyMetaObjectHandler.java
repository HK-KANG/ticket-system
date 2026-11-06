package com.example.ticket.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 公共字段自动填充。
 *
 * <p>为什么需要它：create_time / update_time 这两列几乎每张表都有，
 * 如果每个 Service 里手写 {@code entity.setCreateTime(LocalDateTime.now())}，
 * 总有几处会忘，最后数据库里出现 NULL 或者时间不一致。
 *
 * <p>这里配合实体字段上的 {@code @TableField(fill = FieldFill.INSERT)} 使用，
 * 由 MP 在插入/更新前统一填充。
 *
 * <p>注意：数据库列本身也写了 DEFAULT CURRENT_TIMESTAMP(3)，
 * 那是给「不经过 Java 的写入」兜底的（比如手工插数据），两者不冲突。
 */
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    /**
     * 插入时填充创建时间与更新时间。
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
    }

    /**
     * 更新时只填充更新时间，创建时间不动。
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}
