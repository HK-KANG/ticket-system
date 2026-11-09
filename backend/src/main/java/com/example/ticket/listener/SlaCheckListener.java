package com.example.ticket.listener;

import com.example.ticket.config.RabbitConfig;
import com.example.ticket.mq.SlaDelayMessage;
import com.example.ticket.service.SlaService;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * SLA 延迟消息消费者。
 *
 * <p>手动 ack（配置里 acknowledge-mode: manual）：处理成功才确认，处理失败不重回队列——
 * 因为定时扫描会兜底，反复重投只会让日志变脏、还可能拖慢整个队列。
 *
 * <p>拿到消息不直接信任它：重新查库判断是否真的超时。消息可能是几分钟前投递的，
 * 这期间工单可能已经被处理甚至关闭了。
 */
@Component
@Slf4j
public class SlaCheckListener {

    private final SlaService slaService;

    public SlaCheckListener(SlaService slaService) {
        this.slaService = slaService;
    }

    @RabbitListener(queues = RabbitConfig.CHECK_QUEUE)
    public void onSlaCheck(SlaDelayMessage msg, Channel channel,
                           @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        if (msg == null || msg.ticketId() == null) {
            log.warn("SLA 消息内容为空，丢弃");
            channel.basicNack(deliveryTag, false, false);
            return;
        }
        try {
            boolean escalated = slaService.escalateIfDue(msg.ticketId(), "MQ");
            if (escalated) {
                log.info("SLA 到期消息触发升级 ticketId={}", msg.ticketId());
            } else {
                log.debug("SLA 消息到达但无需升级 ticketId={}", msg.ticketId());
            }
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("处理 SLA 消息失败 ticketId={}", msg.ticketId(), e);
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
