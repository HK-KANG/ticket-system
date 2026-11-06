package com.example.ticket.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户/坐席实体，对应表 t_user。
 * 一个账号可以是客户、坐席或管理员，靠 role 区分。
 */
@Data
@TableName("t_user")
public class User {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录名，唯一 */
    private String username;

    /** BCrypt 密文，绝不存明文 */
    private String password;

    /** 真实姓名 */
    private String realName;

    /** 角色：CUSTOMER / AGENT / ADMIN */
    private String role;

    /** 技能组，抢单和派单的匹配依据 */
    private String skillGroup;

    /** 在线状态：ONLINE / OFFLINE / BUSY */
    private String status;

    /** 同时处理工单上限，派单时判满 */
    private Integer maxLoad;

    /** 创建时间，由数据库默认值填充 */
    private LocalDateTime createTime;

    /** 更新时间，由数据库自动维护 */
    private LocalDateTime updateTime;

    /** 逻辑删除标记：0 正常 / 1 已删。
     *  MyBatis-Plus 会自动给所有查询加上 deleted = 0 条件 */
    @TableLogic
    private Integer deleted;
}
