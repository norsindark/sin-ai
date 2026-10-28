package com.sinai.ai.internal.llm;

import com.sinai.common.constant.Language;
import java.lang.Character.UnicodeScript;
import java.text.Normalizer;
import java.util.EnumSet;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

final class ScriptPolicy {

    private static final Set<UnicodeScript> CJK_SCRIPTS = EnumSet.of(
            UnicodeScript.HAN, UnicodeScript.HIRAGANA, UnicodeScript.KATAKANA, UnicodeScript.HANGUL);
    private static final int MIN_WORDS_FOR_LANGUAGE_CHECK = 3;

    private ScriptPolicy() {
    }

    static Set<UnicodeScript> allowedCjkScripts(Language target) {
        return switch (target) {
            case CHINESE -> EnumSet.of(UnicodeScript.HAN);
            case JAPANESE -> EnumSet.of(UnicodeScript.HAN, UnicodeScript.HIRAGANA, UnicodeScript.KATAKANA);
            case KOREAN -> EnumSet.of(UnicodeScript.HANGUL);
            default -> EnumSet.noneOf(UnicodeScript.class);
        };
    }

    static boolean containsForeignScript(String text, Language target) {
        if (StringUtils.isEmpty(text)) {
            return false;
        }
        var allowed = allowedCjkScripts(target);
        return text.codePoints().anyMatch(codePoint -> isForeign(codePoint, allowed));
    }

    static String stripForeignScript(String text, Language target) {
        if (StringUtils.isEmpty(text)) {
            return StringUtils.EMPTY;
        }
        var allowed = allowedCjkScripts(target);
        var builder = new StringBuilder();
        text.codePoints()
                .filter(codePoint -> !isForeign(codePoint, allowed))
                .forEach(builder::appendCodePoint);
        return StringUtils.normalizeSpace(builder.toString());
    }

    static boolean looksUnlikeTarget(String text, Language target) {
        if (target != Language.VIETNAMESE || StringUtils.isBlank(text)) {
            return false;
        }
        var words = StringUtils.split(StringUtils.normalizeSpace(text));
        if (words.length < MIN_WORDS_FOR_LANGUAGE_CHECK) {
            return false;
        }
        return !hasVietnameseMarks(text);
    }

    private static boolean hasVietnameseMarks(String text) {
        var decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
        return decomposed.codePoints().anyMatch(codePoint ->
                (codePoint >= 0x0300 && codePoint <= 0x036F) || codePoint == 'đ' || codePoint == 'Đ');
    }

    private static boolean isForeign(int codePoint, Set<UnicodeScript> allowed) {
        var script = UnicodeScript.of(codePoint);
        return CJK_SCRIPTS.contains(script) && !allowed.contains(script);
    }
}
