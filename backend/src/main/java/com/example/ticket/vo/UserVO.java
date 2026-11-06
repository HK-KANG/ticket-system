package com.example.ticket.vo;

import com.example.ticket.entity.User;
import lombok.Data;

/**
 * 返回给前端的用户信息。
 *
 * <p>刻意不返回 password 字段——直接把实体类丢给前端是这个项目最容易犯的错，
 * 一旦哪天有人给实体加了手机号、身份证，就跟着漏出去了。
 */
@Data
public class UserVO {

    private Long id;

    private String username;

    private String realName;

    /** CUSTOMER / AGENT / ADMIN */
    private String role;

    /** 技能组，客户为 null */
    private String skillGroup;

    /** 同时在办上限，仅坐席有意义 */
    private Integer maxLoad;

    /**
     * 从实体转换，只挑需要的字段。
     */
    public static UserVO from(User user) {
        if (user == null) {
            return null;
        }
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setRole(user.getRole());
        vo.setSkillGroup(user.getSkillGroup());
        vo.setMaxLoad(user.getMaxLoad());
        return vo;
    }
}
