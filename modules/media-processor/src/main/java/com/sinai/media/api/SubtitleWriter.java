package com.sinai.media.api;

import com.sinai.common.dto.SubtitleSegment;
import java.nio.file.Path;
import java.util.List;

public interface SubtitleWriter {

    void write(List<SubtitleSegment> segments, Path srtFile);
}
