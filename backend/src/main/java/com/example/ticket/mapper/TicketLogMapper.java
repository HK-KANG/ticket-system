package com.example.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.ticket.entity.TicketLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工单流转日志数据访问。
 */
@Mapper
public interface TicketLogMapper extends BaseMapper<TicketLog> {
}
