package com.sinai.ai.api;

import com.sinai.common.constant.Language;
import com.sinai.common.dto.SubtitleSegment;
import java.util.List;

public interface SubtitleTranslator {

    List<SubtitleSegment> translate(List<SubtitleSegment> segments, Language source, Language target);
}
