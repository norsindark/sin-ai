package com.sinai.ai.api;

import com.sinai.common.constant.Language;
import com.sinai.common.dto.SubtitleSegment;
import java.util.List;

public interface SubtitleTranslator {

    default List<SubtitleSegment> translate(List<SubtitleSegment> segments, Language source, Language target) {
        return translate(segments, source, target, ProgressListener.NONE);
    }

    List<SubtitleSegment> translate(List<SubtitleSegment> segments, Language source, Language target,
            ProgressListener progress);
}
