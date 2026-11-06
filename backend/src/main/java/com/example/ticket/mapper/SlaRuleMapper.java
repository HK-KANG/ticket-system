package com.example.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.ticket.entity.SlaRule;
import org.apache.ibatis.annotations.Mapper;

/**
 * SLA 规则数据访问。
 */
@Mapper
public interface SlaRuleMapper extends BaseMapper<SlaRule> {
}
