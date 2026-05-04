package com.example.resourceservice.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

@Component
public class ResourceEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ResourceEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final String routingKey;

    public ResourceEventPublisher(RabbitTemplate rabbitTemplate,
                                  @Value("${rabbitmq.exchange}") String exchange,
                                  @Value("${rabbitmq.routing-key}") String routingKey) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.routingKey = routingKey;
    }

    @Retryable(
            retryFor = {AmqpException.class},
            maxAttemptsExpression = "#{${retry.max-attempts}}",
            backoff = @Backoff(
                    delayExpression = "#{${retry.initial-interval}}",
                    multiplierExpression = "#{${retry.multiplier}}",
                    maxDelayExpression = "#{${retry.max-delay}}"
            )
    )
    public void publish(Long resourceId) {
        ResourceUploadedEvent event = new ResourceUploadedEvent(resourceId);
        rabbitTemplate.convertAndSend(exchange, routingKey, event, message -> {
            message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            return message;
        });
        log.info("Published ResourceUploadedEvent for resourceId={}", resourceId);
    }
}
