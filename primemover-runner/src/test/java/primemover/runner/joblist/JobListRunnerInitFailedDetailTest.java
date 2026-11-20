package primemover.runner.joblist;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JobListRunnerInitFailedDetailTest {

	@Test
	@DisplayName("the reason of a failed startup is part of the queue's message")
	void reasonIsAppended() {
		assertEquals(
				"browser/profile startup did not complete for crawler 2036: "
						+ "Multilogin GET_PROXY_CONNECTION_IP_ERROR (couldn't get proxy connection ip data)",
				JobListRunner.initFailedDetail(2036,
						"Multilogin GET_PROXY_CONNECTION_IP_ERROR (couldn't get proxy connection ip data)"));
	}

	@Test
	@DisplayName("no known reason leaves the message as it was")
	void noReason() {
		assertEquals("browser/profile startup did not complete for crawler 7",
				JobListRunner.initFailedDetail(7, null));
		assertEquals("browser/profile startup did not complete for crawler 7",
				JobListRunner.initFailedDetail(7, " "));
	}
}
