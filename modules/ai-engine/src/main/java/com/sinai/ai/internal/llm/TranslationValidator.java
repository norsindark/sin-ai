package com.sinai.ai.internal.llm;

import com.sinai.common.constant.Language;
import com.sinai.common.dto.SubtitleSegment;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;

final class TranslationValidator {

    static final double MIN_SOURCE_SIMILARITY = 0.5;

    static final int MIN_UNITS_FOR_LENGTH_CHECK = 4;
    static final double MIN_LENGTH_RATIO = 0.35;

    private static final Pattern NON_LETTER_OR_DIGIT = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern NON_LETTER_OR_DIGIT_OR_WIDE = Pattern.compile(
            "[^\\p{L}\\p{N}]+|[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}\\p{IsHangul}]");

    private TranslationValidator() {
    }

    static List<String> findProblems(List<SubtitleSegment> batch, Map<Integer, TranslatedLine> translated,
            Language target) {
        var problems = new ArrayList<String>();
        var checksAlignment = batch.size() > 1;
        var sourceByTranslation = new HashMap<String, String>();
        for (var segment : batch) {
            var line = translated.get(segment.index());
            if (Objects.isNull(line) || StringUtils.isBlank(line.text())) {
                problems.add("line " + segment.index() + " is missing");
                continue;
            }
            if (checksAlignment && similarity(line.source(), segment.text()) < MIN_SOURCE_SIMILARITY) {
                problems.add("line " + segment.index() + " echoes a different source line");
            }
            if (normalize(line.text()).isEmpty()) {
                problems.add("line " + segment.index() + " has no words");
                continue;
            }
            if (isTooShort(segment.text(), line.text())) {
                problems.add("line " + segment.index() + " is cut short");
            }
            if (ScriptPolicy.containsForeignScript(line.text(), target)) {
                problems.add("line " + segment.index() + " contains characters outside " + target.getDisplayName());
            }
            if (ScriptPolicy.looksUnlikeTarget(line.text(), target)) {
                problems.add("line " + segment.index() + " is not written in " + target.getDisplayName());
            }
            if (checksAlignment) {
                var normalizedText = normalize(line.text());
                var normalizedSource = normalize(segment.text());
                var previousSource = sourceByTranslation.putIfAbsent(normalizedText, normalizedSource);
                if (Objects.nonNull(previousSource) && !previousSource.equals(normalizedSource)) {
                    problems.add("line " + segment.index() + " repeats the translation of another line");
                }
            }
        }
        return problems;
    }

    static boolean isTooShort(String source, String translation) {
        var sourceUnits = units(source);
        return sourceUnits >= MIN_UNITS_FOR_LENGTH_CHECK && units(translation) < sourceUnits * MIN_LENGTH_RATIO;
    }

    static int units(String text) {
        if (StringUtils.isBlank(text)) {
            return 0;
        }
        var wideCharacters = (int) text.codePoints().filter(TranslationValidator::isWide).count();
        var words = StringUtils.split(NON_LETTER_OR_DIGIT_OR_WIDE.matcher(text).replaceAll(" ")).length;
        return wideCharacters + words;
    }

    private static boolean isWide(int codePoint) {
        var script = Character.UnicodeScript.of(codePoint);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL;
    }

    static double similarity(String left, String right) {
        var normalizedLeft = normalize(left);
        var normalizedRight = normalize(right);
        if (normalizedLeft.isEmpty() || normalizedRight.isEmpty()) {
            return normalizedLeft.equals(normalizedRight) ? 1.0 : 0.0;
        }
        if (normalizedLeft.equals(normalizedRight)) {
            return 1.0;
        }
        var leftBigrams = bigrams(normalizedLeft);
        var rightBigrams = bigrams(normalizedRight);
        if (leftBigrams.isEmpty() || rightBigrams.isEmpty()) {
            return 0.0;
        }
        var remaining = new ArrayList<>(rightBigrams);
        var shared = 0;
        for (var bigram : leftBigrams) {
            if (remaining.remove(bigram)) {
                shared++;
            }
        }
        return 2.0 * shared / (leftBigrams.size() + rightBigrams.size());
    }

    static String normalize(String text) {
        if (StringUtils.isBlank(text)) {
            return StringUtils.EMPTY;
        }
        var composed = Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        return NON_LETTER_OR_DIGIT.matcher(composed).replaceAll("");
    }

    private static List<String> bigrams(String text) {
        var codePoints = text.codePoints().toArray();
        var result = new ArrayList<String>();
        if (codePoints.length == 1) {
            result.add(new String(codePoints, 0, 1));
            return result;
        }
        for (var index = 0; index + 1 < codePoints.length; index++) {
            result.add(new String(codePoints, index, 2));
        }
        return result;
    }

    record TranslatedLine(int index, String source, String text) {
    }
}
