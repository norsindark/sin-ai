package com.sinai.ai.internal.whisper;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;

final class HallucinationFilter {

    private static final Pattern REPEATED_UNIT = Pattern.compile("(\\p{IsLatin}{2,}?)\\1{2,}");
    private static final Pattern NON_LETTER_OR_DIGIT = Pattern.compile("[^\\p{L}\\p{N}]+");

    private static final List<String> KNOWN_PHRASES = List.of(
            "zitherharp",
            "以下是普通话的句子",
            "thankyouforwatching",
            "thanksforwatching",
            "pleasesubscribe",
            "subtitlesbytheamaraorgcommunity",
            "字幕由amaraorg社区提供",
            "请不吝点赞订阅转发打赏支持明镜与点点栏目",
            "ご視聴ありがとうございました",
            "hãysubscribechokênhghiềnmìgõđểkhôngbỏlỡnhữngvideohấpdẫn");

    private HallucinationFilter() {
    }

    static boolean isHallucination(String text) {
        if (StringUtils.isBlank(text)) {
            return true;
        }
        if (REPEATED_UNIT.matcher(text).find()) {
            return true;
        }
        var compact = NON_LETTER_OR_DIGIT.matcher(text.toLowerCase(Locale.ROOT)).replaceAll("");
        return KNOWN_PHRASES.stream().anyMatch(compact::contains);
    }
}
