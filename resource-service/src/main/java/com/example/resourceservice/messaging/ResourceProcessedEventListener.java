package com.example.resourceservice.messaging;

import com.example.resourceservice.service.ResourceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ResourceProcessedEventListener {

    private static final Logger log = LoggerFactory.getLogger(ResourceProcessedEventListener.class);

    private final ResourceService resourceService;

    public ResourceProcessedEventListener(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    @RabbitListener(queues = "${rabbitmq.processed-queue}")
    public void handleResourceProcessed(ResourceProcessedEvent event) {
        Long resourceId = event.resourceId();
        log.info("Received ResourceProcessedEvent for resourceId={}", resourceId);
        resourceService.promoteResource(resourceId);
        log.info("Completed promotion for resourceId={}", resourceId);
    }
}
