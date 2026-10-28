package com.sinai.app.config;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.jvm.convention.otel.OpenTelemetryJvmClassLoadingMeterConventions;
import io.micrometer.core.instrument.binder.jvm.convention.otel.OpenTelemetryJvmCpuMeterConventions;
import io.micrometer.core.instrument.binder.jvm.convention.otel.OpenTelemetryJvmMemoryMeterConventions;
import io.micrometer.core.instrument.binder.jvm.convention.otel.OpenTelemetryJvmThreadMeterConventions;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.observation.ObservationPredicate;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.observation.OpenTelemetryServerRequestObservationConvention;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.security.config.observation.SecurityObservationSettings;

@Configuration
public class ObservabilityConfig {

    static final String SCHEDULED_TASK_OBSERVATION = "tasks.scheduled.execution";
    static final List<String> IGNORED_PATH_PREFIXES = List.of("/actuator", "/v3/api-docs");
    static final String JVM_METER_PREFIX = "jvm.";
    static final Map<String, String> JVM_ANNOTATION_UNITS = Map.of("classes", "{class}", "threads", "{thread}");

    @Bean
    public ObservationPredicate ignoreSchedulerAndHealthChecks() {
        return (name, context) -> {
            if (SCHEDULED_TASK_OBSERVATION.equals(name)) {
                return false;
            }
            if (context instanceof ServerRequestObservationContext serverContext
                    && Objects.nonNull(serverContext.getCarrier())) {
                var path = Objects.requireNonNullElse(serverContext.getCarrier().getRequestURI(), "");
                return IGNORED_PATH_PREFIXES.stream().noneMatch(path::startsWith);
            }
            return true;
        };
    }

    @Bean
    public MeterFilter openTelemetryJvmAnnotationUnits() {
        return new MeterFilter() {
            @Override
            public Meter.Id map(Meter.Id id) {
                if (!id.getName().startsWith(JVM_METER_PREFIX) || Objects.isNull(id.getBaseUnit())) {
                    return id;
                }
                var annotationUnit = JVM_ANNOTATION_UNITS.get(id.getBaseUnit());
                return Objects.isNull(annotationUnit) ? id : id.withBaseUnit(annotationUnit);
            }
        };
    }

    @Bean
    public OpenTelemetryServerRequestObservationConvention openTelemetryServerRequestObservationConvention() {
        return new OpenTelemetryServerRequestObservationConvention();
    }

    @Bean
    public OpenTelemetryJvmMemoryMeterConventions openTelemetryJvmMemoryMeterConventions() {
        return new OpenTelemetryJvmMemoryMeterConventions(Tags.empty());
    }

    @Bean
    public OpenTelemetryJvmCpuMeterConventions openTelemetryJvmCpuMeterConventions() {
        return new OpenTelemetryJvmCpuMeterConventions(Tags.empty());
    }

    @Bean
    public OpenTelemetryJvmThreadMeterConventions openTelemetryJvmThreadMeterConventions() {
        return new OpenTelemetryJvmThreadMeterConventions(Tags.empty());
    }

    @Bean
    public OpenTelemetryJvmClassLoadingMeterConventions openTelemetryJvmClassLoadingMeterConventions() {
        return new OpenTelemetryJvmClassLoadingMeterConventions(Tags.empty());
    }

    @Bean
    public SecurityObservationSettings securityObservationSettings() {
        return SecurityObservationSettings.noObservations();
    }

    @Bean
    public InitializingBean openTelemetryAppenderInitializer(OpenTelemetry openTelemetry) {
        return () -> OpenTelemetryAppender.install(openTelemetry);
    }
}
