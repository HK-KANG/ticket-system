package com.example.ticket.mq;

/**
 * SLA 延迟消息体。
 *
 * <p>只放 ticketId 就够——消费者拿到 id 后会重新查库判断是否真的超时，
 * 不相信消息里的状态（消息可能是几分钟前投的，这期间工单可能已经被处理了）。
 */
public record SlaDelayMessage(Long ticketId) {
}
