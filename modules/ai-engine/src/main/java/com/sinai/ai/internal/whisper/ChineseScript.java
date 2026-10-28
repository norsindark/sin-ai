package com.sinai.ai.internal.whisper;

import com.github.houbb.opencc4j.util.ZhConverterUtil;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;

final class ChineseScript {

    private static final Pattern ZHU_PARTICLE = Pattern.compile(
            "(?<![显卓昭土名巨原论专编译拙遗新])著(?![名作称述录者])");

    private ChineseScript() {
    }

    static String toSimplified(String text) {
        if (StringUtils.isBlank(text)) {
            return text;
        }
        return ZHU_PARTICLE.matcher(ZhConverterUtil.toSimple(text)).replaceAll("着");
    }
}
