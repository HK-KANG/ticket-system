package com.example.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.ticket.entity.Idempotent;
import org.apache.ibatis.annotations.Mapper;

/**
 * 幂等记录数据访问。
 */
@Mapper
public interface IdempotentMapper extends BaseMapper<Idempotent> {
}
