package com.sinai.workflow.model.request;

import com.sinai.workflow.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public record CreateJobRequest(
        @Schema(description = "Source video (.mp4)", requiredMode = Schema.RequiredMode.REQUIRED)
        MultipartFile file,
        @Schema(ref = OpenApiConfig.LANGUAGE_CODE_REF, requiredMode = Schema.RequiredMode.REQUIRED)
        String sourceLanguage,
        @ArraySchema(schema = @Schema(ref = OpenApiConfig.LANGUAGE_CODE_REF), minItems = 1, uniqueItems = true,
                arraySchema = @Schema(description = "Repeat the field once per target language; must not contain the"
                        + " source language", requiredMode = Schema.RequiredMode.REQUIRED))
        List<String> targetLanguages) {
}
