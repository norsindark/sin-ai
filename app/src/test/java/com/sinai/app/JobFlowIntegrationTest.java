package com.sinai.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sinai.common.constant.JobStatus;
import com.sinai.common.constant.Language;
import com.sinai.storage.api.ObjectStorage;
import com.sinai.storage.config.StorageProperties;
import com.sinai.workflow.repository.SubtitleJobRepository;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import jakarta.persistence.EntityManagerFactory;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = {"spring.jpa.properties.hibernate.generate_statistics=true",
        "sinai.workflow.worker.enabled=false", "sinai.workflow.temp-directory=${java.io.tmpdir}/sin-ai-test"})
@AutoConfigureMockMvc
@EnabledIf("infrastructureAvailable")
class JobFlowIntegrationTest {

    private static final byte[] MP4_BYTES = {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm', 0, 0, 2, 0};

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SubtitleJobRepository jobRepository;

    @Autowired
    private ObjectStorage objectStorage;

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private StorageProperties storageProperties;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @TempDir
    Path tempDir;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final List<UUID> createdJobIds = new ArrayList<>();
    private final List<String> createdObjectKeys = new ArrayList<>();
    private final List<String> createdProcessedKeys = new ArrayList<>();

    static boolean infrastructureAvailable() {
        var required = List.of("SIN_AI_DB_PASSWORD", "STORAGE_APP_ACCESS_KEY", "STORAGE_APP_SECRET_KEY");
        if (required.stream().anyMatch(name -> StringUtils.isBlank(System.getenv(name)))) {
            return false;
        }
        var postgresPort = Integer.parseInt(StringUtils.defaultIfBlank(System.getenv("POSTGRES_PORT"), "5432"));
        var storagePort = Integer.parseInt(StringUtils.defaultIfBlank(System.getenv("STORAGE_API_PORT"), "9000"));
        return isReachable(postgresPort) && isReachable(storagePort);
    }

    @AfterEach
    void cleanUp() throws Exception {
        for (var key : createdObjectKeys) {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(storageProperties.rawBucket())
                    .object(key)
                    .build());
        }
        for (var key : createdProcessedKeys) {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(storageProperties.processedBucket())
                    .object(key)
                    .build());
        }
        for (var jobId : createdJobIds) {
            var videoIds = jdbcTemplate.queryForList(
                    "SELECT CAST(video_id AS VARCHAR) FROM subtitle_jobs WHERE id = CAST(? AS UUID)", String.class,
                    jobId.toString());
            jdbcTemplate.update("DELETE FROM subtitle_jobs WHERE id = CAST(? AS UUID)", jobId.toString());
            for (var videoId : videoIds) {
                jdbcTemplate.update("DELETE FROM videos WHERE id = CAST(? AS UUID)", videoId);
            }
        }
    }

    @Test
    void uploadStoresVideoInMinioAndPendingJobInDatabase() throws Exception {
        var jobId = createJob("lecture.mp4", "en", "vi", "ja");

        var job = jobRepository.findWithDetailsById(jobId).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(job.getSourceLanguage()).isEqualTo(Language.ENGLISH);
        assertThat(job.getVersion()).isZero();
        assertThat(job.getVideo().getStorageBucket()).isEqualTo(storageProperties.rawBucket());
        assertThat(job.getTranslationTasks())
                .extracting(task -> task.getTargetLanguage().getCode())
                .containsExactly("ja", "vi");
        var downloaded = tempDir.resolve("downloaded.mp4");
        objectStorage.download(job.getVideo().getStorageBucket(), job.getVideo().getStorageKey(), downloaded);
        assertThat(Files.readAllBytes(downloaded)).isEqualTo(MP4_BYTES);

        mockMvc.perform(get("/api/v1/jobs/{id}", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.sourceLanguage").value("en"))
                .andExpect(jsonPath("$.video.originalFilename").value("lecture.mp4"))
                .andExpect(jsonPath("$.video.sizeBytes").value(MP4_BYTES.length))
                .andExpect(jsonPath("$.translations[0].targetLanguage").value("ja"))
                .andExpect(jsonPath("$.translations[1].targetLanguage").value("vi"));
    }

    @Test
    void invalidUploadLeavesNoRowsBehind() throws Exception {
        var before = jobRepository.count();

        mockMvc.perform(multipart("/api/v1/jobs")
                        .file(new MockMultipartFile("file", "fake.mp4", "video/mp4", "not a video".getBytes()))
                        .param("sourceLanguage", "en")
                        .param("targetLanguages", "vi"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE_TYPE"));

        assertThat(jobRepository.count()).isEqualTo(before);
    }

    @Test
    void listJobsUsesConstantNumberOfQueries() throws Exception {
        for (var i = 0; i < 4; i++) {
            createJob("clip-" + i + ".mp4", "en", "vi", "ja", "ko");
        }
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        var body = mockMvc.perform(get("/api/v1/jobs").param("size", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].targetLanguages.length()").value(3))
                .andReturn().getResponse().getContentAsString();

        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(3);
        assertThat(statistics.getEntityFetchCount()).isZero();
        assertThat(statistics.getCollectionFetchCount()).isZero();
        var page = jsonMapper.readTree(body);
        assertThat(page.path("totalElements").asLong()).isGreaterThanOrEqualTo(4);
        assertThat(page.path("items").get(0).path("targetLanguages").toString()).isEqualTo("[\"ja\",\"ko\",\"vi\"]");
    }

    @Test
    void getJobLoadsDetailsInSingleQuery() throws Exception {
        var jobId = createJob("single.mp4", "vi", "en", "fr");
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        mockMvc.perform(get("/api/v1/jobs/{id}", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.translations.length()").value(2));

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    void renameKeepsOriginalFilenameAndNamesDownloadsAfterTitle() throws Exception {
        var jobId = createJob("lecture.mp4", "en", "vi");
        var subtitleKey = "jobs/" + jobId + "/subtitles/en.srt";
        uploadProcessed(subtitleKey, "1\n00:00:00,000 --> 00:00:01,000\nHello\n");
        jdbcTemplate.update("UPDATE subtitle_jobs SET status = 'PROCESSING', started_at = now(), "
                + "source_subtitle_key = ? WHERE id = CAST(? AS UUID)", subtitleKey, jobId.toString());

        var body = mockMvc.perform(patch("/api/v1/jobs/{id}", jobId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"  Họp nhóm tuần 3  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.video.title").value("Họp nhóm tuần 3"))
                .andExpect(jsonPath("$.video.originalFilename").value("lecture.mp4"))
                .andReturn().getResponse().getContentAsString();

        var subtitleUrl = jsonMapper.readTree(body).path("sourceSubtitleUrl").asString();
        var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(subtitleUrl)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        var disposition = ContentDisposition.parse(response.headers().firstValue("Content-Disposition").orElseThrow());
        assertThat(disposition.isAttachment()).isTrue();
        assertThat(disposition.getFilename()).isEqualTo("Họp nhóm tuần 3.en.srt");
        assertThat(jobRepository.findWithDetailsById(jobId).orElseThrow().getVersion()).isZero();

        mockMvc.perform(get("/api/v1/jobs").param("size", "100"))
                .andExpect(jsonPath("$.items[?(@.id == '" + jobId + "')].title").value("Họp nhóm tuần 3"));
    }

    @Test
    void renameRejectsBlankTitle() throws Exception {
        var jobId = createJob("blank.mp4", "en", "vi");

        mockMvc.perform(patch("/api/v1/jobs/{id}", jobId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }

    @Test
    void deleteRemovesRowsAndEveryStoredObject() throws Exception {
        var jobId = createJob("delete-me.mp4", "en", "vi", "ja");
        var job = jobRepository.findWithDetailsById(jobId).orElseThrow();
        var videoId = job.getVideo().getId().toString();
        var rawKey = job.getVideo().getStorageKey();
        var processedKeys = List.of("jobs/" + jobId + "/output.mp4", "jobs/" + jobId + "/subtitles/en.srt",
                "jobs/" + jobId + "/subtitles/vi.srt", "jobs/" + jobId + "/subtitles/ja.srt");
        for (var key : processedKeys) {
            uploadProcessed(key, "content of " + key);
        }

        mockMvc.perform(delete("/api/v1/jobs/{id}", jobId))
                .andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM subtitle_jobs WHERE id = CAST(? AS UUID)",
                Long.class, jobId.toString())).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM translation_tasks WHERE job_id = CAST(? AS UUID)", Long.class,
                jobId.toString())).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM videos WHERE id = CAST(? AS UUID)",
                Long.class, videoId)).isZero();
        assertThat(objectExists(storageProperties.rawBucket(), rawKey)).isFalse();
        for (var key : processedKeys) {
            assertThat(objectExists(storageProperties.processedBucket(), key)).as(key).isFalse();
        }
        mockMvc.perform(get("/api/v1/jobs/{id}", jobId))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteIsRejectedWhileProcessingAndUnknownJobIsNotFound() throws Exception {
        var jobId = createJob("busy.mp4", "en", "vi");
        jdbcTemplate.update("UPDATE subtitle_jobs SET status = 'PROCESSING', started_at = now() "
                + "WHERE id = CAST(? AS UUID)", jobId.toString());

        mockMvc.perform(delete("/api/v1/jobs/{id}", jobId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("JOB_IN_PROGRESS"));
        assertThat(jobRepository.findById(jobId)).isPresent();

        mockMvc.perform(delete("/api/v1/jobs/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("JOB_NOT_FOUND"));
    }

    @Test
    void openApiDocumentsMultipartContractWithLanguageCodes() throws Exception {
        var body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var docs = jsonMapper.readTree(body);

        var languageCodes = new ArrayList<String>();
        docs.path("components").path("schemas").path("LanguageCode").path("enum")
                .forEach(node -> languageCodes.add(node.asString()));
        assertThat(languageCodes).containsExactlyElementsOf(
                Arrays.stream(Language.values()).map(Language::getCode).toList());
        var createJob = docs.path("paths").path("/api/v1/jobs").path("post");
        assertThat(createJob.path("parameters").isMissingNode() || createJob.path("parameters").isEmpty()).isTrue();
        var multipartSchemaRef = createJob.path("requestBody").path("content").path("multipart/form-data")
                .path("schema").path("$ref").asString();
        var schemaName = StringUtils.substringAfterLast(multipartSchemaRef, "/");
        var properties = docs.path("components").path("schemas").path(schemaName).path("properties");
        assertThat(properties.path("file").path("format").asString()).isEqualTo("binary");
        assertThat(properties.path("sourceLanguage").path("$ref").asString()).endsWith("/LanguageCode");
        assertThat(properties.path("targetLanguages").path("items").path("$ref").asString()).endsWith("/LanguageCode");
        assertThat(docs.path("components").path("schemas").path("ApiErrorResponse").path("required").toString())
                .isEqualTo("[\"code\",\"message\"]");
    }

    private UUID createJob(String filename, String sourceLanguage, String... targetLanguages) throws Exception {
        var body = mockMvc.perform(multipart("/api/v1/jobs")
                        .file(new MockMultipartFile("file", filename, "video/mp4", MP4_BYTES))
                        .param("sourceLanguage", sourceLanguage)
                        .param("targetLanguages", targetLanguages))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        var jobId = UUID.fromString(jsonMapper.readTree(body).path("id").asString());
        createdJobIds.add(jobId);
        createdObjectKeys.add(jobRepository.findWithDetailsById(jobId).orElseThrow().getVideo().getStorageKey());
        return jobId;
    }

    private void uploadProcessed(String key, String content) throws Exception {
        var file = Files.writeString(tempDir.resolve(UUID.randomUUID() + ".tmp"), content, StandardCharsets.UTF_8);
        objectStorage.upload(storageProperties.processedBucket(), key, file, "application/octet-stream");
        createdProcessedKeys.add(key);
    }

    private boolean objectExists(String bucket, String key) {
        try {
            minioClient.statObject(StatObjectArgs.builder().bucket(bucket).object(key).build());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isReachable(int port) {
        try (var socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", port), 500);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
