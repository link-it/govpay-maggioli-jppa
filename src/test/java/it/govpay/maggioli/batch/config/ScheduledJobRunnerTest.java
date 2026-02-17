package it.govpay.maggioli.batch.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;

import it.govpay.common.batch.runner.JobExecutionHelper;
import it.govpay.common.batch.runner.JobExecutionHelper.PreExecutionCheckResult;
import it.govpay.common.batch.runner.JobExecutionHelper.PreExecutionResult;
import it.govpay.maggioli.batch.Costanti;

class ScheduledJobRunnerTest {

    @Mock
    private JobExecutionHelper jobExecutionHelper;
    @Mock
    private Job maggioliJppaNotificationJob;

    private ScheduledJobRunner runner;

    private static final String JOB_NAME = Costanti.MAGGIOLI_JPPA_NOTIFICATION_JOB_NAME;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        runner = new ScheduledJobRunner(jobExecutionHelper, maggioliJppaNotificationJob);
    }

    @Test
    void whenJobRunningOnAnotherNode_thenSkipLaunching() throws Exception {
        when(jobExecutionHelper.checkBeforeExecution(JOB_NAME))
            .thenReturn(new PreExecutionResult(PreExecutionCheckResult.RUNNING_ON_OTHER_NODE, null, "OtherNode"));

        JobExecution result = runner.runBatchMaggioliJppaNotificationJob();

        assertNull(result);
        verify(jobExecutionHelper, never()).runJob(any(), any());
    }

    @Test
    void whenJobRunningOnSameNode_thenAlsoSkip() throws Exception {
        when(jobExecutionHelper.checkBeforeExecution(JOB_NAME))
            .thenReturn(new PreExecutionResult(PreExecutionCheckResult.RUNNING_ON_THIS_NODE, null, "GovPay-Maggioli-JPPA-Batch"));

        JobExecution result = runner.runBatchMaggioliJppaNotificationJob();

        assertNull(result);
        verify(jobExecutionHelper, never()).runJob(any(), any());
    }

    @Test
    void whenNoJobRunning_thenLaunchJob() throws Exception {
        when(jobExecutionHelper.checkBeforeExecution(JOB_NAME))
            .thenReturn(new PreExecutionResult(PreExecutionCheckResult.CAN_PROCEED, null, null));

        JobExecution launched = new JobExecution(2L);
        when(jobExecutionHelper.runJob(eq(maggioliJppaNotificationJob), eq(JOB_NAME)))
            .thenReturn(launched);

        JobExecution result = runner.runBatchMaggioliJppaNotificationJob();

        assertNotNull(result);
        verify(jobExecutionHelper).runJob(eq(maggioliJppaNotificationJob), eq(JOB_NAME));
    }

    @Test
    void whenJobIsStaleAndAbandonmentSucceeds_thenLaunchNewJob() throws Exception {
        when(jobExecutionHelper.checkBeforeExecution(JOB_NAME))
            .thenReturn(new PreExecutionResult(PreExecutionCheckResult.STALE_ABANDONED_CAN_PROCEED, null, null));

        JobExecution launched = new JobExecution(2L);
        when(jobExecutionHelper.runJob(eq(maggioliJppaNotificationJob), eq(JOB_NAME)))
            .thenReturn(launched);

        JobExecution result = runner.runBatchMaggioliJppaNotificationJob();

        assertNotNull(result);
        verify(jobExecutionHelper).runJob(eq(maggioliJppaNotificationJob), eq(JOB_NAME));
    }

    @Test
    void whenJobIsStaleAndAbandonmentFails_thenDoNotLaunchNewJob() throws Exception {
        when(jobExecutionHelper.checkBeforeExecution(JOB_NAME))
            .thenReturn(new PreExecutionResult(PreExecutionCheckResult.STALE_ABANDON_FAILED, null, null));

        JobExecution result = runner.runBatchMaggioliJppaNotificationJob();

        assertNull(result);
        verify(jobExecutionHelper, never()).runJob(any(), any());
    }
}
