package com.example.resourceprocessor.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ResourceMessageListener {

    private static final Logger log = LoggerFactory.getLogger(ResourceMessageListener.class);

    @RabbitListener(queues = "${rabbitmq.queue}")
    public void handleResourceUploaded(ResourceUploadedEvent event) {
        log.info("Received ResourceUploadedEvent for resourceId={}", event.resourceId());
    }
}
