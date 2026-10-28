package com.sinai.ai.internal.whisper;

import static org.assertj.core.api.Assertions.assertThat;

import com.sinai.ai.internal.whisper.CueSegmenter.Cue;
import com.sinai.ai.internal.whisper.CueSegmenter.TimedWord;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CueSegmenterTest {

    @Test
    void keepsShortContiguousWordsInOneCue() {
        var cues = CueSegmenter.split(List.of(
                new TimedWord(1200, 2160, " Ngày"),
                new TimedWord(2160, 3280, " thay"),
                new TimedWord(3280, 3800, " đêm")));

        assertThat(cues).containsExactly(new Cue(1200, 3800, "Ngày thay đêm"));
    }

    @Test
    void splitsAtLongGapBetweenWords() {
        var cues = CueSegmenter.split(List.of(
                new TimedWord(88700, 89300, " Mai"),
                new TimedWord(90700, 91200, " sau,"),
                new TimedWord(110400, 110600, " ra"),
                new TimedWord(110600, 111100, " khơi")));

        assertThat(cues).containsExactly(
                new Cue(88700, 91200, "Mai sau,"),
                new Cue(110400, 111100, "ra khơi"));
    }

    @Test
    void movesStrandedLeadingWordIntoTheNextCue() {
        var cues = CueSegmenter.split(List.of(
                new TimedWord(27300, 27900, " Bởi"),
                new TimedWord(45000, 45200, " đây"),
                new TimedWord(45200, 45400, " với"),
                new TimedWord(45400, 45900, " tôi")));

        assertThat(cues).containsExactly(new Cue(45000, 45900, "Bởi đây với tôi"));
    }

    @Test
    void movesStrandedTrailingWordIntoThePreviousCue() {
        var cues = CueSegmenter.split(List.of(
                new TimedWord(1000, 1500, " Luôn"),
                new TimedWord(1500, 2000, " cạnh"),
                new TimedWord(9000, 9500, " nhau")));

        assertThat(cues).containsExactly(new Cue(1000, 2000, "Luôn cạnh nhau"));
    }

    @Test
    void splitsWhenCueWouldExceedMaxDuration() {
        var cues = CueSegmenter.split(List.of(
                new TimedWord(0, 3000, " one"),
                new TimedWord(3000, 6000, " two"),
                new TimedWord(6000, 9000, " three"),
                new TimedWord(9000, 10000, " four")));

        assertThat(cues).containsExactly(
                new Cue(0, 6000, "one two"),
                new Cue(6000, 10000, "three four"));
    }

    @Test
    void capsSingleLongWordAtMaxDuration() {
        var cues = CueSegmenter.split(List.of(new TimedWord(1000, 20000, " ôi")));

        assertThat(cues).containsExactly(new Cue(1000, 1000 + CueSegmenter.MAX_CUE_DURATION_MS, "ôi"));
    }

    @Test
    void splitsWhenCueWouldExceedMaxWidthCountingCjkAsDouble() {
        var words = "我曾经跨过山河大海也穿过人山人海我曾经拥有着的一切".codePoints()
                .mapToObj(Character::toString)
                .toList();
        var timedWords = new ArrayList<TimedWord>();
        for (var index = 0; index < words.size(); index++) {
            timedWords.add(new TimedWord(index * 200L, index * 200L + 200, words.get(index)));
        }

        var cues = CueSegmenter.split(timedWords);

        assertThat(cues).extracting(Cue::text)
                .containsExactly("我曾经跨过山河大海也穿过", "人山人海我曾经拥有着的一切");
        assertThat(cues).allSatisfy(cue ->
                assertThat(CueSegmenter.width(cue.text())).isLessThanOrEqualTo(CueSegmenter.MAX_CUE_WIDTH));
    }

    @Test
    void balancesLongLineInsteadOfLeavingAnOrphanWord() {
        var text = List.of(" Chẳng", " phải", " phép", " màu", " về", " sao", " chúng", " ta", " gặp", " nhau");
        var timedWords = new ArrayList<TimedWord>();
        for (var index = 0; index < text.size(); index++) {
            timedWords.add(new TimedWord(68940 + index * 450L, 68940 + index * 450L + 400, text.get(index)));
        }

        var cues = CueSegmenter.split(timedWords);

        assertThat(cues).extracting(Cue::text)
                .containsExactly("Chẳng phải phép màu về", "sao chúng ta gặp nhau");
    }
}
