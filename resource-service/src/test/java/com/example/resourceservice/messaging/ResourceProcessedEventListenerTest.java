package com.example.resourceservice.messaging;

import com.example.resourceservice.service.ResourceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class ResourceProcessedEventListenerTest {

    @Mock
    private ResourceService resourceService;

    @InjectMocks
    private ResourceProcessedEventListener listener;

    @Test
    void handleResourceProcessed_callsPromoteResource() {
        listener.handleResourceProcessed(new ResourceProcessedEvent(1L));

        verify(resourceService).promoteResource(1L);
    }

    @Test
    void handleResourceProcessed_propagatesExceptionOnFailure() {
        doThrow(new RuntimeException("S3 unavailable"))
                .when(resourceService).promoteResource(42L);

        assertThatThrownBy(() -> listener.handleResourceProcessed(new ResourceProcessedEvent(42L)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("S3 unavailable");
    }
}
