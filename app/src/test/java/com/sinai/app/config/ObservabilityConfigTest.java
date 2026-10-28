package com.sinai.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tags;
import org.junit.jupiter.api.Test;

class ObservabilityConfigTest {

    private final ObservabilityConfig config = new ObservabilityConfig();

    @Test
    void mapsJvmCountUnitsToOpenTelemetryAnnotations() {
        var filter = config.openTelemetryJvmAnnotationUnits();

        assertThat(filter.map(id("jvm.class.count", "classes")).getBaseUnit()).isEqualTo("{class}");
        assertThat(filter.map(id("jvm.thread.count", "threads")).getBaseUnit()).isEqualTo("{thread}");
    }

    @Test
    void keepsOtherUnitsAndNonJvmMeters() {
        var filter = config.openTelemetryJvmAnnotationUnits();

        assertThat(filter.map(id("jvm.memory.used", "bytes")).getBaseUnit()).isEqualTo("bytes");
        assertThat(filter.map(id("jvm.cpu.count", null)).getBaseUnit()).isNull();
        assertThat(filter.map(id("executor.pool.size", "threads")).getBaseUnit()).isEqualTo("threads");
    }

    private static Meter.Id id(String name, String baseUnit) {
        return new Meter.Id(name, Tags.empty(), baseUnit, null, Meter.Type.GAUGE);
    }
}
