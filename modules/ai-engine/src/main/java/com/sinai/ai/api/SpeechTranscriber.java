package com.sinai.ai.api;

import com.sinai.common.constant.Language;
import com.sinai.common.dto.SubtitleSegment;
import java.nio.file.Path;
import java.util.List;

public interface SpeechTranscriber {

    List<SubtitleSegment> transcribe(Path wav, Language language);
}
