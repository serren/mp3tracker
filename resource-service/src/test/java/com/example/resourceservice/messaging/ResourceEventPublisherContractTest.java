package com.example.resourceservice.messaging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * Messaging contract test for ResourceEventPublisher.
 *
 * Verifies the message contract: when an MP3 is uploaded, resource-service MUST publish
 * a message to the "resources.direct" exchange with routing key "resources.uploaded"
 * containing a ResourceUploadedEvent payload with the resource's id.
 *
 * This test documents and verifies the message schema that resource-processor (consumer)
 * depends on. Any change to the exchange name, routing key, or payload structure here
 * is a breaking contract change.
 */
@ExtendWith(MockitoExtension.class)
class ResourceEventPublisherContractTest {

    @Mock
    RabbitTemplate rabbitTemplate;

    @InjectMocks
    ResourceEventPublisher publisher;

    @Test
    void publish_sendsEventToCorrectExchangeWithCorrectRoutingKey() {
        ReflectionTestUtils.setField(publisher, "exchange", "resources.direct");
        ReflectionTestUtils.setField(publisher, "routingKey", "resources.uploaded");

        publisher.publish(1L);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate).convertAndSend(
                eq("resources.direct"),
                eq("resources.uploaded"),
                payloadCaptor.capture(),
                any(MessagePostProcessor.class)
        );

        Object payload = payloadCaptor.getValue();
        assertThat(payload).isInstanceOf(ResourceUploadedEvent.class);
        assertThat(((ResourceUploadedEvent) payload).resourceId()).isEqualTo(1L);
    }

    @Test
    void publish_forDifferentResourceId_eventCarriesThatId() {
        ReflectionTestUtils.setField(publisher, "exchange", "resources.direct");
        ReflectionTestUtils.setField(publisher, "routingKey", "resources.uploaded");

        publisher.publish(99L);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate).convertAndSend(
                eq("resources.direct"),
                eq("resources.uploaded"),
                payloadCaptor.capture(),
                any(MessagePostProcessor.class)
        );

        assertThat(((ResourceUploadedEvent) payloadCaptor.getValue()).resourceId()).isEqualTo(99L);
    }
}
