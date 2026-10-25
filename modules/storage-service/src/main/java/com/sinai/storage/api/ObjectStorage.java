package com.sinai.storage.api;

import java.nio.file.Path;
import java.time.Duration;

public interface ObjectStorage {

    void upload(String bucket, String key, Path file, String contentType);

    void download(String bucket, String key, Path target);

    String presignedGetUrl(String bucket, String key, Duration expiry);
}
