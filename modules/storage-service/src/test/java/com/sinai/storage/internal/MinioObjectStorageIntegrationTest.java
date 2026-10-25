package com.sinai.storage.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.sinai.common.exception.ErrorCode;
import com.sinai.common.exception.SinAiException;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MinioObjectStorageIntegrationTest {

    private static final String HOST = "localhost";

    private MinioClient client;
    private MinioObjectStorage storage;
    private String bucket;
    private String key;

    @TempDir
    Path tempDir;

    @BeforeEach
    void connect() {
        var accessKey = System.getenv("STORAGE_APP_ACCESS_KEY");
        var secretKey = System.getenv("STORAGE_APP_SECRET_KEY");
        assumeTrue(StringUtils.isNoneBlank(accessKey, secretKey), "storage credentials not set");
        var port = Integer.parseInt(StringUtils.defaultIfBlank(System.getenv("STORAGE_API_PORT"), "9000"));
        assumeTrue(isReachable(port), "MinIO is not reachable on port " + port);
        bucket = StringUtils.defaultIfBlank(System.getenv("STORAGE_RAW_BUCKET"), "raw-videos");
        key = "it/" + UUID.randomUUID() + ".txt";
        client = MinioClient.builder()
                .endpoint("http://" + HOST + ":" + port)
                .credentials(accessKey, secretKey)
                .build();
        storage = new MinioObjectStorage(client);
    }

    @AfterEach
    void cleanUp() throws Exception {
        if (Objects.nonNull(client) && StringUtils.isNotBlank(key)) {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
        }
    }

    @Test
    void uploadDownloadAndPresignedUrlRoundTrip() throws Exception {
        var content = "xin chào sin-ai";
        var source = Files.writeString(tempDir.resolve("source.txt"), content, StandardCharsets.UTF_8);

        storage.upload(bucket, key, source, "text/plain");

        var downloaded = tempDir.resolve("nested").resolve("downloaded.txt");
        Files.createDirectories(downloaded.getParent());
        storage.download(bucket, key, downloaded);
        assertThat(Files.readString(downloaded, StandardCharsets.UTF_8)).isEqualTo(content);

        storage.download(bucket, key, downloaded);
        assertThat(Files.readString(downloaded, StandardCharsets.UTF_8)).isEqualTo(content);

        var url = storage.presignedGetUrl(bucket, key, Duration.ofMinutes(5));
        var response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(content);
    }

    @Test
    void downloadOfMissingObjectFailsWithStorageError() {
        var target = tempDir.resolve("missing.txt");

        assertThatThrownBy(() -> storage.download(bucket, "it/does-not-exist-" + UUID.randomUUID(), target))
                .isInstanceOfSatisfying(SinAiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.STORAGE_ERROR));
    }

    private static boolean isReachable(int port) {
        try (var socket = new Socket()) {
            socket.connect(new InetSocketAddress(HOST, port), 500);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
