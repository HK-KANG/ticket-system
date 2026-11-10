package com.example.ticket.listener;

import com.example.ticket.config.RabbitConfig;
import com.example.ticket.entity.ExportTask;
import com.example.ticket.mq.ExportMessage;
import com.example.ticket.service.ExportService;
import com.example.ticket.service.TicketExcelExporter;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 导出任务消费者。
 *
 * <p>三步：抢占任务 → 生成文件 → 落结果。任何一步失败都要把任务标成 FAILED，
 * 因为用户在前端盯着状态，卡在 RUNNING 最难受。
 *
 * <p>手动 ack + 不重回队列：导出失败通常是数据或磁盘问题，重投大概率还是失败，
 * 不如让用户看到失败原因后重新发起。
 */
@Component
@Slf4j
public class ExportListener {

    private final ExportService exportService;
    private final TicketExcelExporter exporter;

    public ExportListener(ExportService exportService, TicketExcelExporter exporter) {
        this.exportService = exportService;
        this.exporter = exporter;
    }

    @RabbitListener(queues = RabbitConfig.EXPORT_QUEUE)
    public void onExport(ExportMessage msg, Channel channel,
                         @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        if (msg == null || msg.taskId() == null) {
            log.warn("导出消息内容为空，丢弃");
            channel.basicNack(deliveryTag, false, false);
            return;
        }
        Long taskId = msg.taskId();
        try {
            if (!exportService.claim(taskId)) {
                log.info("导出任务已被处理或不在排队状态，跳过 taskId={}", taskId);
                channel.basicAck(deliveryTag, false);
                return;
            }
            ExportTask task = exportService.findById(taskId);
            if (task == null) {
                log.warn("导出任务不存在 taskId={}", taskId);
                channel.basicAck(deliveryTag, false);
                return;
            }
            TicketExcelExporter.Exported result = exporter.export(task.getParams(), taskId);
            exportService.markSuccess(taskId, task.getTaskNo() + "-tickets.xlsx",
                    result.path(), result.size(), result.rows());
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("导出失败 taskId={}", taskId, e);
            try {
                exportService.fail(taskId, e.getMessage());
            } catch (Exception ignore) {
                // 连标记失败都失败就没辙了，日志里已经记了原始异常
            }
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
