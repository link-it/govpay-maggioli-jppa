package it.govpay.maggioli.batch.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import it.govpay.common.batch.dto.BatchInfo;
import it.govpay.common.batch.runner.JobExecutionHelper;
import it.govpay.common.client.service.ConnettoreService;
import it.govpay.maggioli.batch.Costanti;
import jakarta.persistence.EntityManager;

@DisplayName("BatchController")
class BatchControllerTest {

    private JobExecutionHelper jobExecutionHelper;
    private JobRepository jobRepository;
    private Job maggioliJppaNotificationJob;
    private ConnettoreService connettoreService;
    private Environment environment;
    private ZoneId applicationZoneId;
    private EntityManager entityManager;

    private BatchController controller;

    @BeforeEach
    void setUp() {
        jobExecutionHelper = mock(JobExecutionHelper.class);
        jobRepository = mock(JobRepository.class);
        maggioliJppaNotificationJob = mock(Job.class);
        connettoreService = mock(ConnettoreService.class);
        environment = mock(Environment.class);
        applicationZoneId = ZoneId.of("Europe/Rome");
        entityManager = mock(EntityManager.class);

        controller = new BatchController(
                jobExecutionHelper,
                jobRepository,
                maggioliJppaNotificationJob,
                environment,
                applicationZoneId,
                600_000L,
                connettoreService,
                entityManager);
    }

    @Test
    @DisplayName("getJob returns the injected job")
    void getJobReturnsInjected() {
        Job result = ReflectionTestUtils.invokeMethod(controller, "getJob");
        assertEquals(maggioliJppaNotificationJob, result);
    }

    @Test
    @DisplayName("getJobName returns MAGGIOLI_JPPA_NOTIFICATION_JOB_NAME constant")
    void getJobNameReturnsConstant() {
        String result = ReflectionTestUtils.invokeMethod(controller, "getJobName");
        assertEquals(Costanti.MAGGIOLI_JPPA_NOTIFICATION_JOB_NAME, result);
    }

    @Test
    @DisplayName("getDisplayName/getDescription return non-blank strings")
    void getDisplayNameAndDescriptionReturnNonBlankStrings() {
        String displayName = ReflectionTestUtils.invokeMethod(controller, "getDisplayName");
        String description = ReflectionTestUtils.invokeMethod(controller, "getDescription");

        assertNotNull(displayName);
        assertNotNull(description);
        assertFalse(displayName.isBlank());
        assertFalse(description.isBlank());
    }

    @Test
    @DisplayName("clearCache delegates to ConnettoreService and returns 200 OK")
    void clearCacheDelegatesAndReturnsOk() {
        ResponseEntity<String> response = ReflectionTestUtils.invokeMethod(controller, "clearCache");

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Cache connettori invalidata", response.getBody());
        verify(connettoreService).clearCache();
    }

    @Test
    @DisplayName("clearCacheEndpoint (inherited) delegates to clearCache")
    void clearCacheEndpointDelegates() {
        ResponseEntity<String> response = controller.clearCacheEndpoint();

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(connettoreService).clearCache();
    }

    @Test
    @DisplayName("info (inherited) returns 200 with jobName/displayName/description")
    void infoEndpointReturnsBatchInfo() {
        ResponseEntity<BatchInfo> response = controller.info();

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        BatchInfo info = response.getBody();
        assertNotNull(info);
        assertEquals(Costanti.MAGGIOLI_JPPA_NOTIFICATION_JOB_NAME, info.getJobName());
        assertEquals(ReflectionTestUtils.invokeMethod(controller, "getDisplayName"), info.getDisplayName());
        assertEquals(ReflectionTestUtils.invokeMethod(controller, "getDescription"), info.getDescription());
    }
}
