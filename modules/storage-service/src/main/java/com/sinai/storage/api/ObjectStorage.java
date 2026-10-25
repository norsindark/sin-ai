package com.sinai.storage.api;

import java.nio.file.Path;
import java.time.Duration;

public interface ObjectStorage {

    void upload(String bucket, String key, Path file, String contentType);

    void download(String bucket, String key, Path target);

    void delete(String bucket, String key);

    String presignedGetUrl(String bucket, String key, Duration expiry);
}
