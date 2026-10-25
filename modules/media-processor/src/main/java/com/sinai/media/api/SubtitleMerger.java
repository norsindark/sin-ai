package com.sinai.media.api;

import com.sinai.common.constant.Language;
import java.nio.file.Path;

public interface SubtitleMerger {

    void merge(Path video, Path srtFile, Language language, Path outputVideo);
}
