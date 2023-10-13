package hsg.bot.e2e;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.ThrowingConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import hsg.bot.e2e.loader.JsonLoader;
import primemover.anticaptcha.CaptchaSolvingType;
import primemover.model.AgentType;
import primemover.model.Crawler;
import primemover.model.Joblist;
import primemover.runner.ITaskOperator;
import primemover.runner.TaskOperatorFactory;

@SpringBootTest
class DynamicE2ETest {

	private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private static String testCasesFolder = "e2e-testcases";

	@Autowired
	private TaskOperatorFactory operatorFactory;

	private static JsonLoader loader;

	@BeforeAll
	static void setUpBeforeClass() throws Exception {
		loader = new JsonLoader();
		loader.setAgentRemapping(AgentType.CHROME);
	}

	private Function<Pair<Crawler, Joblist>, String> displayNameGenerator = (input) -> "test: "
			+ input.getLeft().getName();

	private ThrowingConsumer<Pair<Crawler, Joblist>> testExecutor = (test) -> {
		LOGGER.debug("running test '{}'", test.getLeft().getName());

		ITaskOperator service = operatorFactory.getItem(test.getLeft());
		service.runJobList(test.getRight());
		service.tearDown(true);
		service = null;
	};

	@TestFactory
	public Stream<DynamicTest> generateTestCases() throws IOException {

		File path = new ClassPathResource(testCasesFolder).getFile();

		if (!path.isDirectory()) {
			throw new IllegalArgumentException("test cases folder not found");
		}

		File[] files = path.listFiles();

		List<String> fileList = Stream.of(files).filter(file -> !file.isDirectory()).map(File::getAbsolutePath)
				.collect(Collectors.toUnmodifiableList());

		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("num test cases: {}", fileList.size());
			fileList.stream().forEach((file) -> LOGGER.debug("file found: {}", file));
		}

		Stream<Pair<Crawler, Joblist>> testStream = fileList.stream().flatMap((file) -> {
			LOGGER.debug("loading '{}'", file);

			try {
				return loader.loadTestSuite(file, CaptchaSolvingType.NEVER).stream();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		});

		return DynamicTest.stream(testStream, displayNameGenerator, testExecutor);
	}

}
