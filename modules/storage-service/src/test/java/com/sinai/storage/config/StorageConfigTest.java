package com.sinai.storage.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.sinai.storage.internal.MinioObjectStorage;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class StorageConfigTest {

    private final StorageConfig config = new StorageConfig();

    @Test
    void presignUsesPublicEndpointWithoutContactingIt() {
        var properties = properties("http://127.0.0.1:1", "us-east-1", "http://localhost:2");

        var url = storage(properties).presignedGetUrl("raw-videos", "videos/a/source.mp4", Duration.ofMinutes(5));

        var uri = URI.create(url);
        assertThat(uri.getHost()).isEqualTo("localhost");
        assertThat(uri.getPort()).isEqualTo(2);
        assertThat(uri.getPath()).isEqualTo("/raw-videos/videos/a/source.mp4");
        assertThat(uri.getQuery()).contains("X-Amz-Signature=");
    }

    @Test
    void presignFallsBackToEndpointWhenPublicEndpointIsBlank() {
        var properties = properties("http://127.0.0.1:1", "us-east-1", " ");

        var url = storage(properties).presignedGetUrl("raw-videos", "k", Duration.ofMinutes(5));

        assertThat(URI.create(url).getHost()).isEqualTo("127.0.0.1");
    }

    @Test
    void presignDefaultsRegionSoNoBucketLocationLookupIsNeeded() {
        var properties = properties("http://127.0.0.1:1", null, "http://localhost:2");

        var url = storage(properties).presignedGetUrl("raw-videos", "k", Duration.ofMinutes(5));

        assertThat(url).contains("%2F" + StorageConfig.DEFAULT_REGION + "%2Fs3%2F");
    }

    private MinioObjectStorage storage(StorageProperties properties) {
        return new MinioObjectStorage(config.minioClient(properties), config.presignMinioClient(properties));
    }

    private static StorageProperties properties(String endpoint, String region, String publicEndpoint) {
        return new StorageProperties(endpoint, "access", "secret-key", "raw-videos", "processed-videos", region,
                publicEndpoint);
    }
}
