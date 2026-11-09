package com.example.ticket.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SLA 延迟消息的队列拓扑。
 *
 * <p>为什么这么绕：RabbitMQ 本身不支持"延迟 N 分钟投递"（除非装
 * rabbitmq_delayed_message_exchange 插件，我们镜像里没有）。标准的替代做法是
 * <b>TTL + 死信队列</b>：
 *
 * <pre>
 *   生产者 ──▶ ticket.sla.delay.exchange ──▶ ticket.sla.delay.queue
 *                                              │ 消息在这里躺着，TTL = 距截止的毫秒数
 *                                              │ 到期后变成"死信"
 *                                              ▼
 *                                         ticket.sla.check.exchange
 *                                              │
 *                                              ▼
 *                                         ticket.sla.check.queue ──▶ 消费者
 * </pre>
 *
 * <p><b>这套方案的坑（必须知道）</b>：RabbitMQ 只检查<b>队头</b>消息是否过期。
 * 如果队头是一条 480 分钟后到期的消息，后面 30 分钟到期的消息也得等它出队——
 * 这就是"队头阻塞"。所以延迟消息只能当"尽量准时"的加速通道，
 * 真正的兜底是 {@code SlaScanJob} 每 30 秒扫一次库。
 * 两者都走 {@code escalateOne} 的条件更新，天然幂等，谁先到都只升级一次。
 */
@Configuration
public class RabbitConfig {

    public static final String DELAY_EXCHANGE = "ticket.sla.delay.exchange";
    public static final String DELAY_QUEUE = "ticket.sla.delay.queue";
    public static final String CHECK_EXCHANGE = "ticket.sla.check.exchange";
    public static final String CHECK_QUEUE = "ticket.sla.check.queue";
    public static final String ROUTING_KEY = "ticket.sla";

    @Bean
    public DirectExchange delayExchange() {
        return new DirectExchange(DELAY_EXCHANGE, true, false);
    }

    /**
     * 延迟队列：没有消费者，消息只在这里等 TTL；到期后被投到死信交换机。
     */
    @Bean
    public Queue delayQueue() {
        return QueueBuilder.durable(DELAY_QUEUE)
                .deadLetterExchange(CHECK_EXCHANGE)
                .deadLetterRoutingKey(ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding delayBinding() {
        return BindingBuilder.bind(delayQueue()).to(delayExchange()).with(ROUTING_KEY);
    }

    @Bean
    public DirectExchange checkExchange() {
        return new DirectExchange(CHECK_EXCHANGE, true, false);
    }

    @Bean
    public Queue checkQueue() {
        return QueueBuilder.durable(CHECK_QUEUE).build();
    }

    @Bean
    public Binding checkBinding() {
        return BindingBuilder.bind(checkQueue()).to(checkExchange()).with(ROUTING_KEY);
    }

    /**
     * 消息体用 JSON，别用 Java 序列化——Java 序列化出来的消息别的语言看不懂，
     * 而且反序列化有安全风险。
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
