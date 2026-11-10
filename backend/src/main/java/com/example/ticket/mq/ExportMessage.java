package com.example.ticket.mq;

/**
 * 导出任务消息体。
 *
 * <p>只带 taskId：消费者拿到后自己查库取条件，
 * 这样即使消息重复投递，任务也能靠 status 判断是否已经处理过（幂等）。
 */
public record ExportMessage(Long taskId) {
}
