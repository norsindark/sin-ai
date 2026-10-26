package com.sinai.workflow.service;

import com.sinai.common.constant.Language;
import java.util.List;
import java.util.UUID;

public record DeletedJob(UUID jobId, String videoBucket, String videoKey, List<Language> subtitleLanguages) {
}
