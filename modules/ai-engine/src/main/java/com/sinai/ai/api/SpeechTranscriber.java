package com.sinai.ai.api;

import com.sinai.common.constant.Language;
import com.sinai.common.dto.SubtitleSegment;
import java.nio.file.Path;
import java.util.List;

public interface SpeechTranscriber {

    default List<SubtitleSegment> transcribe(Path wav, Language language) {
        return transcribe(wav, language, ProgressListener.NONE);
    }

    List<SubtitleSegment> transcribe(Path wav, Language language, ProgressListener progress);
}
