package com.sinai.media.api;

import com.sinai.common.constant.Language;
import java.nio.file.Path;

public record SubtitleTrack(Path srtFile, Language language) {
}
