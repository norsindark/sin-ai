package com.sinai.workflow.model.entity;

import com.sinai.common.constant.Language;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Objects;

@Converter(autoApply = true)
public class LanguageConverter implements AttributeConverter<Language, String> {

    @Override
    public String convertToDatabaseColumn(Language language) {
        return Objects.isNull(language) ? null : language.getCode();
    }

    @Override
    public Language convertToEntityAttribute(String code) {
        return Objects.isNull(code) ? null : Language.fromCode(code);
    }
}
