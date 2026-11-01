package com.sinai.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CiDemoFailingTest {

    @Test
    void failsOnPurposeToShowBranchProtection() {
        assertThat(1 + 1).isEqualTo(3);
    }
}
