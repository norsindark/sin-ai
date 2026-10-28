package com.sinai.ai.api;

@FunctionalInterface
public interface ProgressListener {

    ProgressListener NONE = percent -> {
    };

    void onProgress(int percent);
}
