package com.sinai.media.api;

import java.nio.file.Path;
import java.util.List;

public interface SubtitleMerger {

    void merge(Path video, List<SubtitleTrack> tracks, Path outputVideo);
}
