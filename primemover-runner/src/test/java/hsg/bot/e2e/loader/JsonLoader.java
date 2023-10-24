package hsg.bot.e2e.loader;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

import javax.annotation.Nullable;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

import hsg.bot.e2e.model.TestSet;
import primemover.anticaptcha.CaptchaSolvingType;
import primemover.api.converter.PrimemoverApiConverter;
import primemover.api.deserializer.RelaxedZonedDateTimeDeserializer;
import primemover.model.AgentType;
import primemover.model.ChromeAgent;
import primemover.model.Crawler;
import primemover.model.FirefoxAgent;
import primemover.model.IAgent;
import primemover.model.Joblist;

public class JsonLoader {
	private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	@Nullable
	private AgentType agentRemap = null;

	private boolean updateTiming = true;

	private ObjectMapper mapper = new ObjectMapper();

	public JsonLoader() {
		mapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
		mapper.setTimeZone(TimeZone.getDefault());

		SimpleModule module = new SimpleModule();
		module.addDeserializer(ZonedDateTime.class, new RelaxedZonedDateTimeDeserializer(ZoneId.systemDefault()));
		mapper.registerModule(module);
	}

	public void setAgentRemapping(@Nullable AgentType type) {

		if (type == AgentType.MULTILOGIN) {
			throw new IllegalArgumentException("Multilogin is not valid for replacement");
		}

		this.agentRemap = type;
	}

	private Crawler runRemapping(Crawler crawler) {

		if (agentRemap != null) {
			IAgent oldAgent = crawler.getAgent();

			if (agentRemap == AgentType.CHROME) {
				crawler.setAgent(ChromeAgent.remapOf(oldAgent));
			}

			if (agentRemap == AgentType.FIREFOX) {
				crawler.setAgent(FirefoxAgent.remapOf(oldAgent));
			}
		}

		return crawler;
	}

	private Joblist runRemapping(Joblist jobs) {

		if (updateTiming == false) {
			return jobs;
		}

		jobs.setStartTime(ZonedDateTime.now());

		return jobs;
	}

	public List<Pair<Crawler, Joblist>> loadTestSuite(String path, CaptchaSolvingType defaultCaptchaSolving)
			throws StreamReadException, DatabindException, IOException {
		// read in Json
		LOGGER.debug("loading file: '{}'", path);

		TestSet testSet = mapper.readValue(new File(path), TestSet.class);
		List<Pair<Crawler, Joblist>> converted = PrimemoverApiConverter.convertFromApi(testSet.getData(),
				defaultCaptchaSolving);

		// run remapping
		List<Pair<Crawler, Joblist>> adjusted = new ArrayList<>();

		converted.forEach(item -> {
			Pair<Crawler, Joblist> adjustedItem = Pair.of( //
					runRemapping(item.getLeft()), //
					runRemapping(item.getRight()) //
			);
			adjusted.add(adjustedItem);
		});

		return adjusted;
	}

}
