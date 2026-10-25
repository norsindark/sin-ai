package com.sinai.media.api;

import java.nio.file.Path;

public interface AudioExtractor {

    void extractAudio(Path video, Path outputWav);
}
