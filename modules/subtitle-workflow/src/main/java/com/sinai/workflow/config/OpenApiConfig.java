package com.sinai.workflow.config;

import com.sinai.common.constant.Language;
import io.swagger.v3.oas.models.media.StringSchema;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String LANGUAGE_CODE_SCHEMA = "LanguageCode";
    public static final String LANGUAGE_CODE_REF = "#/components/schemas/" + LANGUAGE_CODE_SCHEMA;
    private static final String ERROR_SCHEMA = "ApiErrorResponse";

    @Bean
    public OpenApiCustomizer languageCodeSchemaCustomizer() {
        return openApi -> {
            var schema = new StringSchema();
            schema.setDescription("ISO 639-1 language code");
            Arrays.stream(Language.values()).map(Language::getCode).forEach(schema::addEnumItem);
            var components = openApi.getComponents();
            components.addSchemas(LANGUAGE_CODE_SCHEMA, schema);
            if (Objects.nonNull(components.getSchemas()) && components.getSchemas().containsKey(ERROR_SCHEMA)) {
                components.getSchemas().get(ERROR_SCHEMA).setRequired(List.of("code", "message"));
            }
            openApi.getInfo().title("sin-ai API").version("v1");
        };
    }
}
