package com.example.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.ticket.entity.Ticket;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工单数据访问。
 * 复杂统计（按天分组、按分类分组）用 XML 写在 resources/mapper/TicketMapper.xml。
 */
@Mapper
public interface TicketMapper extends BaseMapper<Ticket> {
}
