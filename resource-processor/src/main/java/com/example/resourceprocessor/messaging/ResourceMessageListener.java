package com.example.resourceprocessor.messaging;

import com.example.resourceprocessor.client.ResourceServiceClient;
import com.example.resourceprocessor.client.SongServiceClient;
import com.example.resourceprocessor.dto.SongMetadata;
import com.example.resourceprocessor.dto.SongRequest;
import com.example.resourceprocessor.service.MetadataExtractorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ResourceMessageListener {

    private static final Logger log = LoggerFactory.getLogger(ResourceMessageListener.class);

    private final ResourceServiceClient resourceServiceClient;
    private final SongServiceClient songServiceClient;
    private final MetadataExtractorService metadataExtractorService;
    private final ResourceEventPublisher eventPublisher;

    public ResourceMessageListener(ResourceServiceClient resourceServiceClient,
                                   SongServiceClient songServiceClient,
                                   MetadataExtractorService metadataExtractorService,
                                   ResourceEventPublisher eventPublisher) {
        this.resourceServiceClient = resourceServiceClient;
        this.songServiceClient = songServiceClient;
        this.metadataExtractorService = metadataExtractorService;
        this.eventPublisher = eventPublisher;
    }

    @RabbitListener(queues = "${rabbitmq.queue}")
    public void handleResourceUploaded(ResourceUploadedEvent event) {
        Long resourceId = event.resourceId();
        log.info("Processing ResourceUploadedEvent for resourceId={}", resourceId);

        byte[] mp3Data = resourceServiceClient.getResource(resourceId);
        SongMetadata metadata = metadataExtractorService.extract(mp3Data);
        SongRequest request = new SongRequest(
                resourceId,
                metadata.getName(),
                metadata.getArtist(),
                metadata.getAlbum(),
                metadata.getDuration(),
                metadata.getYear()
        );
        songServiceClient.saveSong(request);
        log.info("Saved metadata for resourceId={}: name={}", resourceId, metadata.getName());

        eventPublisher.publishProcessed(resourceId);
    }
}
