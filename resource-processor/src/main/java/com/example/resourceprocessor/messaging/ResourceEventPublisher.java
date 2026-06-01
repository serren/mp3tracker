package com.example.resourceprocessor.messaging;

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
    private final String processedRoutingKey;

    public ResourceEventPublisher(RabbitTemplate rabbitTemplate,
                                  @Value("${rabbitmq.exchange}") String exchange,
                                  @Value("${rabbitmq.processed-routing-key}") String processedRoutingKey) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.processedRoutingKey = processedRoutingKey;
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
    public void publishProcessed(Long resourceId) {
        ResourceProcessedEvent event = new ResourceProcessedEvent(resourceId);
        rabbitTemplate.convertAndSend(exchange, processedRoutingKey, event, message -> {
            message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            return message;
        });
        log.info("Published ResourceProcessedEvent for resourceId={}", resourceId);
    }
}
