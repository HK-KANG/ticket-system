package com.example.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.ticket.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户表数据访问。
 * 继承 BaseMapper 后增删改查都已具备，只有复杂查询才需要自己写方法。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
