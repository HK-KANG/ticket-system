package com.example.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.ticket.entity.TicketComment;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工单回复数据访问。
 */
@Mapper
public interface TicketCommentMapper extends BaseMapper<TicketComment> {
}
