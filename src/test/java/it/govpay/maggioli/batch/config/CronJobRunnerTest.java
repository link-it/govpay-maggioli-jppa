package it.govpay.maggioli.batch.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.batch.core.Job;

import it.govpay.common.batch.runner.JobExecutionHelper;

class CronJobRunnerTest {

	@Mock
	private JobExecutionHelper jobExecutionHelper;
	@Mock
	private Job maggioliJppaNotificationJob;

	private CronJobRunner runner;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		runner = new CronJobRunner(jobExecutionHelper, maggioliJppaNotificationJob);
	}

	@Test
	void testConstructor() {
		assertNotNull(runner);
	}
}
