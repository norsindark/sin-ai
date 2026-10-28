package com.sinai.ai.internal.llm;

import com.sinai.common.constant.Language;
import java.util.List;
import org.springframework.util.CollectionUtils;

final class TranslationPrompt {

    private TranslationPrompt() {
    }

    static String system(Language source, Language target, List<String> previousProblems) {
        var builder = new StringBuilder()
                .append("You are a professional subtitle translator. Translate subtitle lines from ")
                .append(source.getDisplayName()).append(" to ").append(target.getDisplayName()).append(".\n")
                .append("Rules:\n")
                .append("- Write natural, fluent, conversational ").append(target.getDisplayName())
                .append(" that a native viewer would say, not a word-for-word translation.\n")
                .append("- Keep each line short and easy to read on screen.\n")
                .append("- If the lines are song lyrics, translate them as natural lyrics: poetic, rhythmic and concise.\n")
                .append("- Keep names, pronouns and forms of address consistent across lines.\n")
                .append("- ").append(scriptRule(target)).append("\n")
                .append("- Translate one-to-one: every item in \"lines\" gets exactly one translation. ")
                .append("Never merge, split, skip, reorder or add lines.\n")
                .append("- For every line return its \"index\", its original text copied unchanged as \"source\", ")
                .append("and the translation as \"text\".\n")
                .append("- \"context_before\" and \"context_after\" are neighbouring lines for understanding only. ")
                .append("Never translate or return them.\n");
        if (target == Language.VIETNAMESE) {
            builder.append("- Use natural Vietnamese pronouns (anh/em, tôi/bạn, mình...) that fit the context ")
                    .append("and keep them the same throughout.\n");
        }
        if (!CollectionUtils.isEmpty(previousProblems)) {
            builder.append("Your previous answer was rejected because: ")
                    .append(String.join("; ", previousProblems))
                    .append(". Fix these problems and follow every rule strictly.\n");
        }
        return builder.toString();
    }

    private static String scriptRule(Language target) {
        return switch (target) {
            case CHINESE -> "Write only in Chinese characters; never use Japanese kana or Korean hangul.";
            case JAPANESE -> "Write only in Japanese; never use Korean hangul.";
            case KOREAN -> "Write only in Korean hangul; never use Chinese characters or Japanese kana.";
            default -> "Write only in " + target.getDisplayName()
                    + " using the Latin alphabet. Never output Chinese, Japanese or Korean characters"
                    + " and never leave words in the source language except proper names.";
        };
    }
}
