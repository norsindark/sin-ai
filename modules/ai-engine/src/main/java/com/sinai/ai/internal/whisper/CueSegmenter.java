package com.sinai.ai.internal.whisper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

final class CueSegmenter {

    static final long MAX_WORD_GAP_MS = 1500;
    static final long MAX_CUE_DURATION_MS = 7000;
    static final int MAX_CUE_WIDTH = 42;

    private CueSegmenter() {
    }

    static List<Cue> split(List<TimedWord> words) {
        if (CollectionUtils.isEmpty(words)) {
            return List.of();
        }
        var phrases = mergeStrandedWords(splitAtGaps(words));
        var cues = new ArrayList<Cue>();
        for (var phrase : phrases) {
            for (var part : balance(phrase)) {
                cues.add(toCue(part));
            }
        }
        return cues;
    }

    private static List<List<TimedWord>> splitAtGaps(List<TimedWord> words) {
        var phrases = new ArrayList<List<TimedWord>>();
        var current = new ArrayList<TimedWord>();
        for (var word : words) {
            if (!current.isEmpty() && isSeparatedByGap(current.getLast(), word)) {
                phrases.add(current);
                current = new ArrayList<>();
            }
            current.add(word);
        }
        phrases.add(current);
        return phrases;
    }

    private static List<List<TimedWord>> mergeStrandedWords(List<List<TimedWord>> phrases) {
        var result = new ArrayList<List<TimedWord>>();
        List<TimedWord> pending = null;
        for (var index = 0; index < phrases.size(); index++) {
            var phrase = new ArrayList<>(phrases.get(index));
            if (Objects.nonNull(pending)) {
                phrase.set(0, prependText(pending, phrase.getFirst()));
                pending = null;
            }
            var isStranded = phrase.size() == 1 && phrases.size() > 1;
            var hasNext = index + 1 < phrases.size();
            if (isStranded && hasNext) {
                pending = phrase;
                continue;
            }
            if (isStranded && !result.isEmpty()) {
                var previous = new ArrayList<>(result.removeLast());
                previous.set(previous.size() - 1, appendText(previous.getLast(), phrase.getFirst()));
                result.add(previous);
                continue;
            }
            result.add(phrase);
        }
        return result;
    }

    private static List<List<TimedWord>> balance(List<TimedWord> phrase) {
        var totalWidth = width(joinText(phrase));
        var startMs = phrase.getFirst().startMs();
        var totalDurationMs = phrase.getLast().endMs() - startMs;
        var partsByWidth = (int) Math.ceil((double) totalWidth / MAX_CUE_WIDTH);
        var partsByDuration = (int) Math.ceil((double) totalDurationMs / MAX_CUE_DURATION_MS);
        var parts = Math.max(1, Math.max(partsByWidth, partsByDuration));
        if (parts == 1 || phrase.size() == 1) {
            return List.of(phrase);
        }
        var byWidth = partsByWidth >= partsByDuration;
        var positions = new double[phrase.size()];
        var consumedWidth = 0;
        for (var index = 0; index < phrase.size(); index++) {
            var word = phrase.get(index);
            if (byWidth) {
                var wordWidth = width(word.text());
                positions[index] = consumedWidth + wordWidth / 2.0;
                consumedWidth += wordWidth;
            } else {
                positions[index] = (word.startMs() + word.endMs()) / 2.0 - startMs;
            }
        }
        var total = byWidth ? (double) consumedWidth : (double) totalDurationMs;
        var result = new ArrayList<List<TimedWord>>();
        for (var part = 0; part < parts; part++) {
            result.add(new ArrayList<>());
        }
        for (var index = 0; index < phrase.size(); index++) {
            var part = (int) Math.min(parts - 1, Math.floor(positions[index] / (total / parts)));
            result.get(Math.max(0, part)).add(phrase.get(index));
        }
        return result.stream().filter(part -> !part.isEmpty()).toList();
    }

    private static boolean isSeparatedByGap(TimedWord before, TimedWord after) {
        return after.startMs() - before.endMs() > MAX_WORD_GAP_MS;
    }

    private static TimedWord prependText(List<TimedWord> stranded, TimedWord target) {
        return new TimedWord(target.startMs(), target.endMs(), joinText(stranded) + target.text());
    }

    private static TimedWord appendText(TimedWord target, TimedWord stranded) {
        return new TimedWord(target.startMs(), target.endMs(), target.text() + stranded.text());
    }

    private static Cue toCue(List<TimedWord> group) {
        var startMs = group.getFirst().startMs();
        var endMs = Math.min(group.getLast().endMs(), startMs + MAX_CUE_DURATION_MS);
        if (endMs <= startMs) {
            endMs = startMs + 1;
        }
        return new Cue(startMs, endMs, normalize(joinText(group)));
    }

    private static String joinText(List<TimedWord> group) {
        var builder = new StringBuilder();
        for (var word : group) {
            builder.append(word.text());
        }
        return builder.toString();
    }

    static String normalize(String text) {
        return StringUtils.normalizeSpace(text);
    }

    static int width(String text) {
        var normalized = normalize(text);
        return normalized.codePoints().map(codePoint -> isWide(codePoint) ? 2 : 1).sum();
    }

    private static boolean isWide(int codePoint) {
        var script = Character.UnicodeScript.of(codePoint);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL;
    }

    record TimedWord(long startMs, long endMs, String text) {
    }

    record Cue(long startMs, long endMs, String text) {
    }
}
