package primeover.runner;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.invoke.MethodHandles;

import org.apache.commons.lang3.time.StopWatch;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.event.annotation.AfterTestClass;

import primemover.runner.TaskExecutor;
import primemover.runner.joblist.IJobListRunner;

@SpringBootTest
public class TaskExecutorTest {
	private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private static long SLEEP_MILLIS = 1000l;

	@Autowired
	private TaskExecutor executor;

	private IJobListRunner createSleepingRunner() {

		return new IJobListRunner() {

			@Override
			public Boolean get() {
				try {
					logger.debug("going to sleep");
					Thread.sleep(SLEEP_MILLIS);
					logger.debug("waking up");
				} catch (InterruptedException e) {
					// ignore
				}
				return true;
			}

			@Override
			public long getJobListId() {
				return -1; // not needed
			}
		};
	}

	@Test
	void willRunAsync() {
		StopWatch watch = new StopWatch();
		watch.start();

		executor.run(createSleepingRunner());
		executor.run(createSleepingRunner());

		watch.stop();
		logger.debug("Time elapsed: {} millis", watch.getTime());

		try {
			Thread.sleep(2 * SLEEP_MILLIS);
		} catch (InterruptedException e) {
		}

		assertTrue(watch.getTime() < SLEEP_MILLIS, "Threads not running async");
	}

	@AfterTestClass
	void cleanup() {
		executor.shutdown();
		executor = null;
	}
}
