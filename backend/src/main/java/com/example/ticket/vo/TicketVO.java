package com.example.ticket.vo;

import com.example.ticket.entity.Ticket;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单列表项。
 *
 * <p>比实体多的两个字段是 creatorName / handlerName：
 * 列表上要显示"谁提的、谁在处理"，但列表页不该为了两个名字去查两次用户表（N+1）。
 * 做法是 Service 里批量查一次用户再回填，见 TicketService#toVOList。
 */
@Data
public class TicketVO {

    private Long id;
    private String ticketNo;
    private String title;
    private String category;
    private String priority;
    private String status;

    /** 状态中文名，前端直接显示，省得前端再维护一份映射 */
    private String statusLabel;

    private Long creatorId;
    private String creatorName;
    private Long handlerId;
    private String handlerName;
    private String skillGroup;

    /** SLA 截止时间 */
    private LocalDateTime slaDeadline;
    private LocalDateTime acceptTime;
    private LocalDateTime finishTime;
    private LocalDateTime createTime;

    /** 是否已超时（当前时间超过 SLA 截止时间且尚未关闭），列表上标红用 */
    private Boolean overdue;

    public static TicketVO from(Ticket t) {
        if (t == null) {
            return null;
        }
        TicketVO vo = new TicketVO();
        vo.setId(t.getId());
        vo.setTicketNo(t.getTicketNo());
        vo.setTitle(t.getTitle());
        vo.setCategory(t.getCategory());
        vo.setPriority(t.getPriority());
        vo.setStatus(t.getStatus());
        vo.setCreatorId(t.getCreatorId());
        vo.setHandlerId(t.getHandlerId());
        vo.setSkillGroup(t.getSkillGroup());
        vo.setSlaDeadline(t.getSlaDeadline());
        vo.setAcceptTime(t.getAcceptTime());
        vo.setFinishTime(t.getFinishTime());
        vo.setCreateTime(t.getCreateTime());
        return vo;
    }
}
