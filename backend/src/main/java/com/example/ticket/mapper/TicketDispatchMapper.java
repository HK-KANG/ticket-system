package com.example.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.ticket.entity.TicketDispatch;
import org.apache.ibatis.annotations.Mapper;

/**
 * 派单记录数据访问。
 */
@Mapper
public interface TicketDispatchMapper extends BaseMapper<TicketDispatch> {
}
