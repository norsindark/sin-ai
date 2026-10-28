package com.sinai.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sinai.common.constant.JobStatus;
import com.sinai.common.constant.Language;
import com.sinai.identity.api.Role;
import com.sinai.identity.model.entity.UserAccount;
import com.sinai.identity.repository.UserAccountRepository;
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
import java.util.Map;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = {"spring.jpa.properties.hibernate.generate_statistics=true",
        "sinai.workflow.worker.enabled=false", "sinai.workflow.temp-directory=${java.io.tmpdir}/sin-ai-test",
        "sinai.security.jwt.secret=" + JobFlowIntegrationTest.JWT_SECRET, "sinai.security.admin.username=",
        "sinai.security.admin.email=",
        "sinai.security.observability.token=" + JobFlowIntegrationTest.OBSERVABILITY_TOKEN,
        "sinai.security.admin.password=", "sinai.security.throttle.max-registrations-per-client=100000",
        "sinai.security.throttle.max-login-failures-per-client=100000"})
@AutoConfigureMockMvc
@EnabledIf("infrastructureAvailable")
class JobFlowIntegrationTest {

    static final String JWT_SECRET = "integration-test-secret-0123456789abcdef";
    static final String OBSERVABILITY_TOKEN = "integration-observability-token-0123456789";
    private static final String PASSWORD = "integration-password";
    private static final String NEW_PASSWORD = "integration-password-2";
    private static final String REFRESH_COOKIE = "sinai_refresh";

    private static final byte[] MP4_BYTES = {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm', 0, 0, 2, 0};

    @Autowired
    private MockMvc anonymousMockMvc;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserAccountRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;
    private String ownerToken;

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
    private final List<UUID> createdUserIds = new ArrayList<>();

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
        for (var userId : createdUserIds) {
            jdbcTemplate.update("DELETE FROM users WHERE id = CAST(? AS UUID)", userId.toString());
        }
    }

    @BeforeEach
    void authenticateOwner() throws Exception {
        ownerToken = registerAndLogin();
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .defaultRequest(get("/").header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .build();
    }

    @Test
    void registerLoginByUsernameOrEmailAndMeRoundTrip() throws Exception {
        var username = newUsername();
        var email = username + "@Sinai.Test";
        var registered = anonymousMockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(username.toUpperCase(), email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.email").value(email.toLowerCase()))
                .andExpect(jsonPath("$.role").value("USER"))
                .andReturn().getResponse().getContentAsString();
        createdUserIds.add(UUID.fromString(jsonMapper.readTree(registered).path("id").asString()));

        anonymousMockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(username, newUsername() + "@sinai.test")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_ALREADY_EXISTS"));
        anonymousMockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(newUsername(), email.toUpperCase())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
        anonymousMockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(username, "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        var token = login(email);
        anonymousMockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username));
        anonymousMockMvc.perform(get("/api/v1/jobs"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void repeatedFailedLoginsAreThrottledWithRetryAfterEvenWithTheRightPassword() throws Exception {
        var username = newUsername();
        registerUser(username);
        for (var attempt = 0; attempt < 5; attempt++) {
            anonymousMockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginJson(username, "wrong-password")))
                    .andExpect(status().isUnauthorized());
        }

        anonymousMockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(username.toUpperCase(), PASSWORD)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"))
                .andExpect(result -> assertThat(Long.parseLong(
                        result.getResponse().getHeader(HttpHeaders.RETRY_AFTER))).isBetween(1L, 900L));
        anonymousMockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("it-unknown-" + UUID.randomUUID().toString().substring(0, 8), PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesTheTokenButNotOtherSessions() throws Exception {
        var username = newUsername();
        registerUser(username);
        var token = login(username);
        var otherSession = login(username);

        anonymousMockMvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        anonymousMockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        anonymousMockMvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized());
        anonymousMockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(otherSession)))
                .andExpect(status().isOk());
    }

    @Test
    void userEndpointsRequireAdminAndDisablingRevokesExistingTokens() throws Exception {
        var adminToken = createAdminAndLogin();
        var username = newUsername();
        var userId = registerUser(username);
        var userToken = login(username);

        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isForbidden());
        anonymousMockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());

        anonymousMockMvc.perform(get("/api/v1/users").param("size", "100")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == '" + userId + "')].username").value(username));
        anonymousMockMvc.perform(patch("/api/v1/users/{id}", userId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        anonymousMockMvc.perform(get("/api/v1/jobs").header(HttpHeaders.AUTHORIZATION, bearer(userToken)))
                .andExpect(status().isUnauthorized());
        anonymousMockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(username, PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void roleChangeInvalidatesTokensIssuedWithTheOldRole() throws Exception {
        var adminToken = createAdminAndLogin();
        var username = newUsername();
        var userId = registerUser(username);
        var userToken = login(username);

        anonymousMockMvc.perform(patch("/api/v1/users/{id}", userId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk());

        anonymousMockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(userToken)))
                .andExpect(status().isUnauthorized());
        anonymousMockMvc.perform(get("/api/v1/users").header(HttpHeaders.AUTHORIZATION, bearer(login(username))))
                .andExpect(status().isOk());
    }

    @Test
    void refreshCookieRotatesAndPasswordChangeEndsEveryOtherSession() throws Exception {
        var username = newUsername();
        registerUser(username);
        var firstLogin = anonymousMockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(username, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse();
        var firstCookie = firstLogin.getCookie(REFRESH_COOKIE);
        assertThat(firstCookie).isNotNull();
        assertThat(firstCookie.isHttpOnly()).isTrue();
        assertThat(firstCookie.getPath()).isEqualTo("/api/v1/auth");
        assertThat(firstCookie.getMaxAge()).isEqualTo(14 * 24 * 3600);
        assertThat(firstLogin.getHeader(HttpHeaders.SET_COOKIE)).contains("SameSite=Strict");

        var refreshed = anonymousMockMvc.perform(post("/api/v1/auth/refresh").cookie(firstCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value(username))
                .andReturn().getResponse();
        var rotatedCookie = refreshed.getCookie(REFRESH_COOKIE);
        assertThat(rotatedCookie.getValue()).isNotEqualTo(firstCookie.getValue());
        anonymousMockMvc.perform(post("/api/v1/auth/refresh").cookie(firstCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        anonymousMockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized());
        var refreshedToken = jsonMapper.readTree(refreshed.getContentAsString()).path("accessToken").asString();
        var otherDeviceToken = login(username);

        anonymousMockMvc.perform(put("/api/v1/auth/me/password")
                        .header(HttpHeaders.AUTHORIZATION, bearer(refreshedToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(Map.of("currentPassword", "wrong-password",
                                "newPassword", NEW_PASSWORD))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"));
        var changed = anonymousMockMvc.perform(put("/api/v1/auth/me/password")
                        .header(HttpHeaders.AUTHORIZATION, bearer(refreshedToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(Map.of("currentPassword", PASSWORD,
                                "newPassword", NEW_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse();
        var newToken = jsonMapper.readTree(changed.getContentAsString()).path("accessToken").asString();
        var newCookie = changed.getCookie(REFRESH_COOKIE);

        for (var staleToken : List.of(refreshedToken, otherDeviceToken)) {
            anonymousMockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(staleToken)))
                    .andExpect(status().isUnauthorized());
        }
        anonymousMockMvc.perform(post("/api/v1/auth/refresh").cookie(rotatedCookie))
                .andExpect(status().isUnauthorized());
        anonymousMockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(newToken)))
                .andExpect(status().isOk());
        anonymousMockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(username, NEW_PASSWORD)))
                .andExpect(status().isOk());

        var loggedOut = anonymousMockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, bearer(newToken))
                        .cookie(newCookie))
                .andExpect(status().isNoContent())
                .andReturn().getResponse();
        assertThat(loggedOut.getCookie(REFRESH_COOKIE).getMaxAge()).isZero();
        anonymousMockMvc.perform(post("/api/v1/auth/refresh").cookie(newCookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changeEmailRequiresTheCurrentPasswordAndAnUnusedEmail() throws Exception {
        var username = newUsername();
        registerUser(username);
        var token = login(username);
        var takenEmail = registerUserAndReturnEmail();
        var newEmail = newUsername() + "@sinai.test";

        anonymousMockMvc.perform(put("/api/v1/auth/me/email")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(Map.of("email", takenEmail,
                                "currentPassword", PASSWORD))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
        var changed = anonymousMockMvc.perform(put("/api/v1/auth/me/email")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(Map.of("email", newEmail.toUpperCase(),
                                "currentPassword", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(newEmail))
                .andReturn().getResponse().getContentAsString();

        anonymousMockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized());
        anonymousMockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION,
                        bearer(jsonMapper.readTree(changed).path("accessToken").asString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(newEmail));
        login(newEmail);
    }

    @Test
    void adminResetsPasswordsAndDeletingAUserTransfersTheirJobsToTheAdmin() throws Exception {
        var adminToken = createAdminAndLogin();
        var adminId = UUID.fromString(jsonMapper.readTree(anonymousMockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andReturn().getResponse().getContentAsString()).path("id").asString());
        var jobId = createJob("transfer.mp4", "en", "vi");
        var ownerId = jobRepository.findById(jobId).orElseThrow().getOwnerId();

        anonymousMockMvc.perform(put("/api/v1/users/{id}/password", ownerId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(Map.of("newPassword", NEW_PASSWORD))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
        anonymousMockMvc.perform(delete("/api/v1/users/{id}", adminId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANNOT_DELETE_SELF"));
        anonymousMockMvc.perform(delete("/api/v1/users/{id}", ownerId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findById(ownerId)).isEmpty();
        assertThat(jobRepository.findById(jobId).orElseThrow().getOwnerId()).isEqualTo(adminId);
        anonymousMockMvc.perform(delete("/api/v1/users/{id}", ownerId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        anonymousMockMvc.perform(delete("/api/v1/jobs/{id}", jobId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isNoContent());
    }

    @Test
    void adminDashboardAggregatesUsersJobsStorageAndRecentActivity() throws Exception {
        var jobId = createJob("dashboard.mp4", "en", "vi");
        var adminToken = createAdminAndLogin();

        mockMvc.perform(get("/api/v1/admin/dashboard"))
                .andExpect(status().isForbidden());
        anonymousMockMvc.perform(get("/api/v1/admin/dashboard").header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users.total").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.users.activeAdmins").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.jobs.byStatus.length()").value(JobStatus.values().length))
                .andExpect(jsonPath("$.jobs.createdLast7Days").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.storage.uploadedBytes")
                        .value(greaterThanOrEqualTo(MP4_BYTES.length)))
                .andExpect(jsonPath("$.dailyJobs.length()").value(14))
                .andExpect(jsonPath("$.recentJobs[?(@.id == '" + jobId + "')].title").value("dashboard"))
                .andExpect(jsonPath("$.recentJobs[?(@.id == '" + jobId + "')].ownerUsername").isNotEmpty())
                .andExpect(jsonPath("$.recentUsers.length()").value(greaterThanOrEqualTo(2)));
    }

    @Test
    void observabilityTokenReachesActuatorOnly() throws Exception {
        anonymousMockMvc.perform(get("/actuator/metrics")
                        .header(HttpHeaders.AUTHORIZATION, bearer(OBSERVABILITY_TOKEN)))
                .andExpect(status().isOk());
        anonymousMockMvc.perform(get("/actuator/info")
                        .header(HttpHeaders.AUTHORIZATION, bearer(OBSERVABILITY_TOKEN)))
                .andExpect(status().isOk());
        anonymousMockMvc.perform(get("/api/v1/jobs").header(HttpHeaders.AUTHORIZATION, bearer(OBSERVABILITY_TOKEN)))
                .andExpect(status().isForbidden());
        anonymousMockMvc.perform(get("/api/v1/users").header(HttpHeaders.AUTHORIZATION, bearer(OBSERVABILITY_TOKEN)))
                .andExpect(status().isForbidden());
        anonymousMockMvc.perform(get("/actuator/metrics")
                        .header(HttpHeaders.AUTHORIZATION, bearer(OBSERVABILITY_TOKEN + "x")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void jobsAreVisibleOnlyToTheirOwnerAndAdmins() throws Exception {
        var jobId = createJob("private.mp4", "en", "vi");
        var otherToken = registerAndLogin();
        var adminToken = createAdminAndLogin();

        mockMvc.perform(get("/api/v1/jobs/{id}", jobId).header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("JOB_NOT_FOUND"));
        mockMvc.perform(patch("/api/v1/jobs/{id}", jobId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"stolen\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/jobs/{id}", jobId).header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/jobs").header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        assertThat(jobRepository.findById(jobId)).isPresent();

        mockMvc.perform(get("/api/v1/jobs/{id}", jobId).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.video.title").value("private"));
        mockMvc.perform(get("/api/v1/jobs").param("size", "100")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(jsonPath("$.items[?(@.id == '" + jobId + "')]").isNotEmpty());
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
        assertThat(docs.path("components").path("securitySchemes").path("bearerAuth").path("scheme").asString())
                .isEqualTo("bearer");
        assertThat(docs.path("paths").path("/api/v1/auth/login").path("post").path("security").toString())
                .isEqualTo("[]");
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

    private static String newUsername() {
        return "it-" + UUID.randomUUID().toString().substring(0, 12);
    }

    private UUID registerUser(String username) throws Exception {
        var body = anonymousMockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(username, username + "@sinai.test")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        var userId = UUID.fromString(jsonMapper.readTree(body).path("id").asString());
        createdUserIds.add(userId);
        return userId;
    }

    private String registerAndLogin() throws Exception {
        var username = newUsername();
        registerUser(username);
        return login(username);
    }

    private String registerUserAndReturnEmail() throws Exception {
        var username = newUsername();
        registerUser(username);
        return username + "@sinai.test";
    }

    private String createAdminAndLogin() throws Exception {
        var username = newUsername();
        var admin = userRepository.saveAndFlush(UserAccount.create(username, username + "@sinai.test",
                passwordEncoder.encode(PASSWORD), Role.ADMIN));
        createdUserIds.add(admin.getId());
        return login(username);
    }

    private String login(String login) throws Exception {
        var body = anonymousMockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(login, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(body).path("accessToken").asString();
    }

    private String registerJson(String username, String email) {
        return jsonMapper.writeValueAsString(Map.of("username", username, "email", email, "password", PASSWORD));
    }

    private String loginJson(String login, String password) {
        return jsonMapper.writeValueAsString(Map.of("login", login, "password", password));
    }

    private static String bearer(String token) {
        return "Bearer " + token;
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
