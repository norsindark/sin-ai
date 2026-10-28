package com.sinai.ai.internal.whisper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ChineseScriptTest {

    @ParameterizedTest
    @CsvSource({
            "我曾經擁有著的一切, 我曾经拥有着的一切",
            "卻望著卻渴望著, 却望着却渴望着",
            "轉眼都飄散逐漸, 转眼都飘散逐渐",
            "他是著名的作家, 他是著名的作家",
            "效果顯著, 效果显著",
            "这是一部名著, 这是一部名著",
            "我曾经跨过山河大海, 我曾经跨过山河大海",
            "Via Via, Via Via"
    })
    void convertsTraditionalToSimplifiedWithZheParticle(String input, String expected) {
        assertThat(ChineseScript.toSimplified(input)).isEqualTo(expected);
    }
}
