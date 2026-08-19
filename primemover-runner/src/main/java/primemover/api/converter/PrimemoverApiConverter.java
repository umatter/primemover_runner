package primemover.api.converter;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.text.StringEscapeUtils;
import org.openqa.selenium.InvalidArgumentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import primemover.anticaptcha.CaptchaSolvingType;
import primemover.api.model.*;
import primemover.model.*;
import primemover.model.Configuration;
import primemover.model.Crawler;
import primemover.model.MediaOutlet;
import primemover.model.Outlet;
import primemover.model.Term;
import primemover.model.jobs.*;
import primemover.model.proxy.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PrimemoverApiConverter {
	private static final Logger LOGGER = LoggerFactory.getLogger(PrimemoverApiConverter.class);

	private PrimemoverApiConverter() {
	}

	private static IProxy convertApiProxy(primemover.api.model.Proxy item, Crawler crawler) {
		LOGGER.debug("converting proxy {} for crawler {}", item, crawler);

		if (item == null) {
			return null;
		}

		HttpProxy res;

		String type = item.getType();
		if (StringUtils.isEmpty(type)) {
			res = new HttpProxy();
		} else {
			type = type.toLowerCase();
			if (type.contains("oxylabs")) {
				OxylabsProxy oxyProxy = new OxylabsProxy();
				oxyProxy.setLocation(crawler.getAgent().getLocation());
				res = oxyProxy;
			} else if (type.contains("brightdata")) {
				BrightdataProxy brProxy = new BrightdataProxy();
				brProxy.setLocation(crawler.getAgent().getLocation());
				brProxy.setOperatingSystem(null); // currently not passed from the agent
				res = brProxy;
			} else if (type.contains("http")) {
				res = new HttpProxy();

				if (type.contains("https")) {
					res.setUseHttps(true);
				}
			} else {
				throw new InvalidArgumentException("Unknown proxy type '" + item.getType()
						+ "'. Supported types are http, https, oxylabs and brightdata."
						+ " (geosurf was removed - the provider no longer exists.)");
			}
		}

		if (StringUtils.isEmpty(item.getHostname())) {
			throw new InvalidArgumentException("Hostname must not be empty");
		}

		if (item.getPort() <= 0) {
			throw new InvalidArgumentException("No valid port: " + item.getPort());
		}

		res.setUrl(item.getHostname());
		res.setPort(item.getPort());
		res.setUsername(item.getUsername());
		res.setPassword(item.getPassword());

		return res;
	}

	private static IAgent convertApiAgent(primemover.api.model.Agent item) {
		LOGGER.debug("converting agent {}", item);

		IAgent agent;

		String ident = item.getIdentification();

		if (StringUtils.isEmpty(ident)) {
			ident = "multilogin";
		}

		ident = ident.toLowerCase();

		if (ident.contains("firefox")) {
			agent = new FirefoxAgent(item.getId(), item.getName(), item.getDescription(), item.getLocation());
		} else if (ident.contains("chrome")) {
			agent = new ChromeAgent(item.getId(), item.getName(), item.getDescription(), item.getLocation());
		} else if (ident.contains("multilogin") || ident.contains("mlx")) {
			MultiLoginVersion version = MultiLoginVersion.ML6;
			if (ident.contains("multilogin_x") || ident.contains("mlx")) {
				version = MultiLoginVersion.MLX;
			}

			String validatedJson = StringEscapeUtils.unescapeJson(item.getMultilogin_profile());
			if (validatedJson != null && validatedJson.startsWith("\"")) {
				validatedJson = validatedJson.substring(1, validatedJson.length() - 1);
			}
			agent = new MultiLoginAgent(item.getId(), item.getName(), item.getDescription(), item.getLocation(),
					item.getMultilogin_id(), version, validatedJson);
		} else {
			throw new InvalidArgumentException("No valid agent found");
		}

		return agent;
	}

	private static Map<String, String> convertApiBehaviourList(List<primemover.api.model.Behaviour> behaviours) {
		LOGGER.debug("converting api behaviour list {}", behaviours);

		HashMap<String, String> map = new HashMap<String, String>();

		for (primemover.api.model.Behaviour item : behaviours) {
			String key = item.getName();
			String value = item.getValue();
			map.put(key, value);
		}

		return map;
	}

	private static <JobType extends BaseJob> void setBaseFields(JobType job, Long id, String name, String description,
			CaptchaSolvingType captchaSolving, boolean isFailsave, boolean isResetPreviousFailureDefault,
			Map<String, String> behaviours) {
		LOGGER.debug("setting base fields for job {}", id);
		job.setId(id);
		job.setName(name);
		job.setDescription(description);
		job.setCaptchaSolving(captchaSolving);
		job.setIsFailsave(isFailsave);
		job.setTask(behaviours.get("task"));

		// set reset field
		boolean isResetPreviousFailure = isResetPreviousFailureDefault;
		String resetFromBehviours = behaviours.get("resetPreviousFailure");
		if (!StringUtils.isEmpty(resetFromBehviours)) {
			isResetPreviousFailure = Boolean.valueOf(resetFromBehviours);
		}
		job.setResetPreviousFailure(isResetPreviousFailure);
	}

	private static <JobType extends BaseJob> void setBaseFields(JobType job, Long id, String name, String description,
			CaptchaSolvingType captchaSolving, boolean resetPreviousFailureDefault, Map<String, String> behaviours) {
		setBaseFields(job, id, name, description, captchaSolving, false, resetPreviousFailureDefault, behaviours);
	}

	private static <JobType extends BaseJob> void setBaseFields(JobType job, Long id, String name, String description,
			CaptchaSolvingType captchaSolving, Map<String, String> behaviours) {
		setBaseFields(job, id, name, description, captchaSolving, false, false, behaviours);
	}

	private static LambdaSelectJob convertApiLambdaSelectJob(Long id, String name, String description,
			CaptchaSolvingType captchaSolving, Map<String, String> behaviours) {
		LOGGER.debug("converting LambdaSelectJob {}", id);
		LambdaSelectJob job = new LambdaSelectJob();
		setBaseFields(job, id, name, description, captchaSolving, behaviours);

		String type = behaviours.get("linkType");
		if (type == null) {
			job.setLinkType(LambdaLinkType.FUNCTION_NAME);
		} else {
			LambdaLinkType.valueOf(type);
		}

		String linkValue = behaviours.get("linkValue");

		if (StringUtils.isEmpty(linkValue)) {
			throw new IllegalArgumentException("paramenter linkValue is missing or empty");
		}
		job.setLinkValue(linkValue);
		job.setUsername(behaviours.get("username"));
		job.setPassword(behaviours.get("password"));

		return job;
	}

	private static VisitJob convertApiVisitJob(Long id, String name, String description,
			CaptchaSolvingType captchaSolving, Map<String, String> behaviours) {
		LOGGER.debug("converting VisitJob {}", id);
		VisitJob job = new VisitJob();
		setBaseFields(job, id, name, description, captchaSolving, true, behaviours); // will reset failure by default!
		job.setUrl(behaviours.get("url"));

		return job;
	}

	private static HandleAlertJob convertApiHandleAlertJob(Long id, String name, String description,
			CaptchaSolvingType captchaSolving, Boolean runFailsave, Map<String, String> behaviours) {
		LOGGER.debug("converting HandleAlertJob {}", id);
		HandleAlertJob job = new HandleAlertJob();
		setBaseFields(job, id, name, description, captchaSolving, runFailsave, false, behaviours);
		if (behaviours.containsKey("seconds")) {
			job.setAction(DialogHandleType.valueOf(behaviours.get("action")));
		} else {
			job.setAction(DialogHandleType.ACCEPT);
		}

		return job;
	}

	private static WaitJob convertApiWaitJob(Long id, String name, String description,
			CaptchaSolvingType captchaSolving, Map<String, String> behaviours) {
		LOGGER.debug("converting WaitJob {}", id);
		WaitJob job = new WaitJob();
		setBaseFields(job, id, name, description, captchaSolving, behaviours);
		if (behaviours.containsKey("seconds")) {
			job.setSeconds(Integer.valueOf(behaviours.get("seconds")));
		} else {
			job.setSeconds(30);
		}

		return job;
	}

	private static EnterTextFieldJob convertApiEnterTextFieldJob(Long id, String name, String description,
			CaptchaSolvingType captchaSolving, Boolean runFailsave, Map<String, String> behaviours) {
		LOGGER.debug("converting EnterTextFieldJob {}", id);
		EnterTextFieldJob job = new EnterTextFieldJob();
		setBaseFields(job, id, name, description, captchaSolving, runFailsave, false, behaviours);
		job.setType(HtmlSelectorType.valueOf(behaviours.get("selectionType")));
		job.setSelector(behaviours.get("selector"));
		job.setValue(behaviours.get("text"));

		// optional fields
		Boolean appendReturn = true;
		if (behaviours.containsKey("appendReturn")) {
			String val = behaviours.get("appendReturn");

			if ("1".equals(val)) {
				appendReturn = true;
			} else if ("0".equals(val)) {
				appendReturn = false;
			} else {
				appendReturn = Boolean.valueOf(val);
			}
		}

		TypingModeType typingMode = TypingModeType.SIMULATED_NOTYPOS;
		if (behaviours.containsKey("typingMode")) {
			typingMode = TypingModeType.valueOf(behaviours.get("typingMode"));
		}

		job.setAppendReturn(appendReturn);
		job.setTypingMode(typingMode);

		return job;
	}

	private static ClickJob convertApiClickJob(Long id, String name, String description,
			CaptchaSolvingType captchaSolving, Boolean runFailsave, Map<String, String> behaviours) {
		LOGGER.debug("converting ClickJob {}", id);
		ClickJob job = new ClickJob();

		setBaseFields(job, id, name, description, captchaSolving, runFailsave, false, behaviours);
		job.setType(HtmlSelectorType.valueOf(behaviours.get("selectionType")));
		job.setSelector(behaviours.get("selector"));

		if (behaviours.containsKey("expectsPageReload")) {
			job.setExpectsPageReload(Boolean.parseBoolean(behaviours.get("expectsPageReload")));
		} else {
			job.setExpectsPageReload(Boolean.FALSE);
		}

		return job;
	}

	private static SwitchToJob convertApiSwitchJob(Long id, String name, String description,
			CaptchaSolvingType captchaSolving, Boolean runFailsave, Map<String, String> behaviours) {
		LOGGER.debug("converting SwitchJob {}", id);
		SwitchToJob job = new SwitchToJob();
		setBaseFields(job, id, name, description, captchaSolving, runFailsave, false, behaviours);
		job.setType(HtmlSelectorType.valueOf(behaviours.get("selectionType")));
		job.setSelector(behaviours.get("selector"));

		return job;
	}

	private static SingleSelectJob convertApiSingleSelectJob(Long id, String name, String description,
			CaptchaSolvingType captchaSolving, Map<String, String> behaviours) {
		LOGGER.debug("converting SingleSelectJob {}", id);
		SingleSelectJob job = new SingleSelectJob();

		// common
		setBaseFields(job, id, name, description, captchaSolving, behaviours);

		String clickSelector = null;
		HtmlSelectorType clickType = null;
		String criteriaSelector = null;
		HtmlSelectorType criteriaType = null;
		DecisionType decisionType = DecisionType.valueOf(behaviours.get("decisionType"));

		boolean dataComplete = false;

		// old job version (version 1)
		if (behaviours.containsKey("selectionType") && behaviours.containsKey("selector")) {
			LOGGER.debug("found simple job version");
			clickSelector = behaviours.get("selector");
			criteriaSelector = clickSelector;
			clickType = HtmlSelectorType.valueOf(behaviours.get("selectionType"));
			criteriaType = clickType;

			// mandatory field for calculated-type
			if (decisionType == DecisionType.CALCULATED) {
				job.setCriteriaExtractor(behaviours.get("criterionExtractor")); // old job version
			}

			dataComplete = true;
		}

		// new job version (version 2)
		if (behaviours.containsKey("clickSelectionType") && behaviours.containsKey("clickSelector")) {
			LOGGER.debug("found extended job version");
			clickSelector = behaviours.get("clickSelector");
			clickType = HtmlSelectorType.valueOf(behaviours.get("clickSelectionType"));

			if (behaviours.containsKey("criteriaSelector") || behaviours.containsKey("criteriaSelectionType")) {
				criteriaSelector = behaviours.get("criteriaSelector");
				criteriaType = HtmlSelectorType.valueOf(behaviours.get("criteriaSelectionType"));
			} else {
				criteriaSelector = clickSelector;
				criteriaType = clickType;
			}

			// mandatory field for calculated-type
			if (decisionType == DecisionType.CALCULATED) {
				job.setCriteriaExtractor(behaviours.get("criteriaExtractor")); // new job version
			}

			dataComplete = true;
		}

		if (!dataComplete) {
			throw new IllegalArgumentException("job data not complete. provide full data for version 1 or version 2.");
		}

		job.setDecisionType(decisionType);
		job.setSelector(clickSelector);
		job.setType(clickType);
		job.setCriteriaSelector(criteriaSelector);
		job.setCriteriaType(criteriaType);

		return job;
	}

	private static ScrollByDurationJob convertApiScrollByDurationJob(Long id, String name, String description,
			CaptchaSolvingType captchaSolving, Map<String, String> behaviours) {
		LOGGER.debug("converting ScrollByDurationJob {}", id);
		ScrollByDurationJob job = new ScrollByDurationJob();
		setBaseFields(job, id, name, description, captchaSolving, behaviours);

		if (behaviours.containsKey("scrollDuration")) {
			job.setDuration(Double.valueOf(behaviours.get("scrollDuration")).intValue());
		} else if (behaviours.containsKey("duration")) {
			job.setDuration(Double.valueOf(behaviours.get("duration")).intValue());
		} else {
			throw new IllegalStateException("no duration or scrollDuration found");
		}
		job.setDirection(ScrollDirection.valueOf(behaviours.get("scrollDirection")));

		return job;
	}

	private static ScrollByLengthJob convertApiScrollByLengthJob(Long id, String name, String description,
			CaptchaSolvingType captchaSolving, Map<String, String> behaviours) {
		LOGGER.debug("converting ApiScrollByLengthJob {}", id);
		ScrollByLengthJob job = new ScrollByLengthJob();
		setBaseFields(job, id, name, description, captchaSolving, behaviours);

		// Support multiple field names for length (length, pixels, scrollLength)
		if (behaviours.containsKey("length")) {
			job.setLength(Integer.valueOf(behaviours.get("length")));
		} else if (behaviours.containsKey("pixels")) {
			job.setLength(Integer.valueOf(behaviours.get("pixels")));
		} else if (behaviours.containsKey("scrollLength")) {
			job.setLength(Integer.valueOf(behaviours.get("scrollLength")));
		} else {
			throw new IllegalStateException("ScrollByLengthJob " + id + ": no length, pixels, or scrollLength found in behaviors");
		}

		// Support missing scrollDirection with sensible default
		if (behaviours.containsKey("scrollDirection")) {
			job.setDirection(ScrollDirection.valueOf(behaviours.get("scrollDirection")));
		} else {
			LOGGER.warn("ScrollByLengthJob {}: scrollDirection not specified, defaulting to DOWN", id);
			job.setDirection(ScrollDirection.DOWN);
		}

		return job;
	}

	private static IJob convertApiJob(primemover.api.model.Job job, CaptchaSolvingType defaultCaptchaSolving) {
		LOGGER.debug("start converting API-Job {} with captcha default {}", job.getId(), defaultCaptchaSolving);
		IJob res;

		// grab behavior list
		Map<String, String> behaviors = convertApiBehaviourList(job.getBehaviors());

		// Anti-Captcha
		CaptchaSolvingType captchaSolving = defaultCaptchaSolving;
		if (behaviors.containsKey("captchaMode")) {
			captchaSolving = CaptchaSolvingType.valueOf(behaviors.get("captchaMode").toUpperCase());
		}

		switch (job.getType().toLowerCase()) {
		case "visitjob":
			res = convertApiVisitJob(job.getId(), job.getName(), job.getDescription(), captchaSolving, behaviors);
			break;
		case "waitjob":
			res = convertApiWaitJob(job.getId(), job.getName(), job.getDescription(), captchaSolving, behaviors);
			break;
		case "tryentertextfieldjob":
			res = convertApiEnterTextFieldJob(job.getId(), job.getName(), job.getDescription(), captchaSolving, true,
					behaviors);
			break;
		case "entertextfieldjob":
			res = convertApiEnterTextFieldJob(job.getId(), job.getName(), job.getDescription(), captchaSolving, false,
					behaviors);
			break;
		case "singleselecturljob": // old API
		case "singleselectjob":
			res = convertApiSingleSelectJob(job.getId(), job.getName(), job.getDescription(), captchaSolving,
					behaviors);
			break;
		case "tryswitchjob":
			res = convertApiSwitchJob(job.getId(), job.getName(), job.getDescription(), captchaSolving, true,
					behaviors);
			break;
		case "switchjob":
			res = convertApiSwitchJob(job.getId(), job.getName(), job.getDescription(), captchaSolving, false,
					behaviors);
			break;
		case "tryclickjob":
			res = convertApiClickJob(job.getId(), job.getName(), job.getDescription(), captchaSolving, true, behaviors);
			break;
		case "clickjob":
			res = convertApiClickJob(job.getId(), job.getName(), job.getDescription(), captchaSolving, false,
					behaviors);
			break;
		case "scrollbydurationjob":
			res = convertApiScrollByDurationJob(job.getId(), job.getName(), job.getDescription(), captchaSolving,
					behaviors);
			break;
		case "scrollbylengthjob":
			res = convertApiScrollByLengthJob(job.getId(), job.getName(), job.getDescription(), captchaSolving,
					behaviors);
			break;
		case "handlealertjob":
			res = convertApiHandleAlertJob(job.getId(), job.getName(), job.getDescription(), captchaSolving, false,
					behaviors);
			break;
		case "tryhandlealertjob":
			res = convertApiHandleAlertJob(job.getId(), job.getName(), job.getDescription(), captchaSolving, true,
					behaviors);
			break;
		case "lambdasingleselectjob":
		case "lambdaselectjob":
			res = convertApiLambdaSelectJob(job.getId(), job.getName(), job.getDescription(), captchaSolving,
					behaviors);
			break;
		case "assertproxyjob": // provider-neutral name from the spec DSL
		case "assertproxybyoxylabsjob":
			res = convertApiAssertProxyJob(job.getId(), job.getName(), job.getDescription(), behaviors);
			break;
		default:
			throw new IllegalArgumentException("Invalid Job type found: " + job.getType());
		}

		return res;
	}

	/**
	 * Assert the exit IP's geolocation via ip.oxylabs.io. Expectations come from the
	 * behaviours {@code ip}, {@code country} and {@code city}; at least one must be
	 * present, which {@link AssertProxyJob} enforces.
	 */
	private static IJob convertApiAssertProxyJob(Long id, String name, String description,
			Map<String, String> behaviors) {
		return new AssertProxyByOxylabsJob(id, name, description, behaviors.get("ip"), behaviors.get("country"),
				behaviors.get("city"));
	}

	private static Configuration convertApiConfiguration(primemover.api.model.Configuration item) {
		LOGGER.debug("converting Configuration {}", item);

		if (item == null) {
			return null;
		}

		Configuration config = new Configuration();
		config.setId(item.getId());
		config.setName(item.getName());
		config.setDescription(item.getDescription());

		if (item.getParams().size() > 0) {
			ConfigurationParam param = item.getParams().get(0);

			SelectionConfiguration selectConfig = new SelectionConfiguration();

			selectConfig.setAlpha(param.getAlpha());
			selectConfig.setBeta(param.getBeta());
			selectConfig.setId(param.getId());
			selectConfig.setKappa(param.getKappa());
			selectConfig.setPi(param.getPi());
			selectConfig.setPsi(param.getPsi());
			selectConfig.setTau(param.getTau());

			HashMap<String, MediaOutlet> outlets = new HashMap<>();
			if (param.getMedia_outlet_urls() == null) {
				LOGGER.warn("media outlet urls are missing");
				// throw new IllegalArgumentException("media outlet urls are missing");
			} else {
				for (primemover.api.model.MediaOutlet apiOutlet : param.getMedia_outlet_urls()) {
					MediaOutlet modelOutlet = new MediaOutlet();
					modelOutlet.setDomain(apiOutlet.getDomain());
					modelOutlet.setPi(apiOutlet.getPi());
					modelOutlet.setUrl(apiOutlet.getUrl());
					outlets.put(modelOutlet.getDomain(), modelOutlet);
				}
			}
			selectConfig.setOutlets(outlets);

			config.setSelectionConfiguration(selectConfig);
		} else {
			config.setSelectionConfiguration(null);
		}

		return config;
	}

	private static Joblist convertApiQueue(primemover.api.model.Queue item, CaptchaSolvingType defaultCaptchaSolving) {
		LOGGER.debug("converting JobList {} with defaultCaptchaSolving", item, defaultCaptchaSolving);

		// convert
		Joblist jobList = new Joblist();

		jobList.setId(item.getId().longValue());
		jobList.setActive(item.isActive());
		jobList.setProcessed(item.isProcessed());
		jobList.setReviewed(item.isReviewed());
		jobList.setStartTime(item.getStart_at());
		jobList.setProcessing(item.isProcessing());

		// verify
		List<primemover.api.model.Job> list = item.getJobs();
		List<IJob> jobs = new ArrayList<IJob>();
		int skippedJobs = 0;

		if (!list.isEmpty()) {
			for (primemover.api.model.Job job : list) {
				try {
					jobs.add(convertApiJob(job, defaultCaptchaSolving));
				} catch (Exception e) {
					LOGGER.error("Failed to convert job {} (type: {}) in queue {}: {}. Skipping this job.",
							job.getId(), job.getType(), item.getId(), e.getMessage());
					skippedJobs++;
				}
			}
			if (skippedJobs > 0) {
				LOGGER.warn("Skipped {} job(s) in queue {} due to conversion errors", skippedJobs, item.getId());
			}
		} else {
			LOGGER.warn("Joblist was empty for queue {}", item.getId());
		}

		jobList.setJobs(jobs);

		return jobList;
	}

	private static List<Pair<Crawler, Joblist>> convertApiCrawler(primemover.api.model.Crawler item,
			CaptchaSolvingType defaultCaptchaSolving) {
		LOGGER.debug("converting Crawler {} with defaultCaptchaSolving", item, defaultCaptchaSolving);

		List<Pair<Crawler, Joblist>> out = new ArrayList<Pair<Crawler, Joblist>>();

		// grab crawler
		Crawler crawler = new Crawler();
		if (item.getId() == null) {
			throw new IllegalArgumentException("id must not be null");
		}
		crawler.setId(item.getId().longValue());
		crawler.setName(item.getName());
		crawler.setDescription(item.getDescription());
		crawler.setAgent(convertApiAgent(item.getAgent()));
		crawler.setProxy(convertApiProxy(item.getProxy(), crawler)); // needs to go after agent as agent contains
																		// required location input
		crawler.setConfig(convertApiConfiguration(item.getConfiguration()));
		crawler.setRawConfiguration(item.getConfiguration()); // Store raw API configuration

		// grab joblists
		List<primemover.api.model.Queue> queues = item.getQueues();

		if (queues != null) {
			for (primemover.api.model.Queue queue : queues) {
				Joblist jobList = convertApiQueue(queue, defaultCaptchaSolving);

				if (jobList == null) {
					LOGGER.debug("adding empty joblist to crawler {}", crawler.getId());
				} else {
					LOGGER.debug("adding joblist {} to crawler {}", jobList.getId(), crawler.getId());
				}

				out.add(Pair.of(crawler, jobList));
			}
		}

		return out;
	}

	public static List<Pair<Crawler, Joblist>> convertFromApi(CrawlerResult input,
			CaptchaSolvingType defaultCaptchaSolving) {
		LOGGER.debug("converting Crawler with joblist {} with defaultCaptchaSolving", input, defaultCaptchaSolving);

		List<Pair<Crawler, Joblist>> res = new ArrayList<Pair<Crawler, Joblist>>();
		List<primemover.api.model.Crawler> data = input.getData();
		int skippedCrawlers = 0;

		if (!data.isEmpty()) {

			for (primemover.api.model.Crawler item : data) {
				try {
					res.addAll(convertApiCrawler(item, defaultCaptchaSolving));
				} catch (Exception e) {
					LOGGER.error("Failed to convert crawler {}: {}. Skipping this crawler.",
							item.getId(), e.getMessage());
					skippedCrawlers++;
				}
			}
			if (skippedCrawlers > 0) {
				LOGGER.warn("Skipped {} crawler(s) due to conversion errors", skippedCrawlers);
			}
		}

		return res;
	}

	public static List<Pair<Crawler, Joblist>> convertFromApi(primemover.api.model.Crawler input,
			CaptchaSolvingType defaultCaptchaSolving) {
		LOGGER.debug("converting Crawler with joblist {} with defaultCaptchaSolving", input, defaultCaptchaSolving);

		List<Pair<Crawler, Joblist>> res = new ArrayList<Pair<Crawler, Joblist>>();
		List<primemover.api.model.Crawler> data = List.of(input);
		int skippedCrawlers = 0;

		if (!data.isEmpty()) {

			for (primemover.api.model.Crawler item : data) {
				try {
					res.addAll(convertApiCrawler(item, defaultCaptchaSolving));
				} catch (Exception e) {
					LOGGER.error("Failed to convert crawler {}: {}. Skipping this crawler.",
							item.getId(), e.getMessage());
					skippedCrawlers++;
				}
			}
			if (skippedCrawlers > 0) {
				LOGGER.warn("Skipped {} crawler(s) due to conversion errors", skippedCrawlers);
			}
		}

		return res;
	}

	public static LocationInfo convertFromApi(IpResult input) {
		LOGGER.debug("converting LocationInfo from {}", input);

		IpResultData data = input.getResult();

		LocationInfo res = new LocationInfo();

		res.setCity(data.getCity());
		res.setCountryCode(data.getCountry_code());
		res.setCountryName(data.getCountry_name());
		res.setIp(data.getIp());
		res.setLatitude(data.getLatitude());
		res.setLongitude(data.getLongitude());
		res.setRegionCode(data.getRegion_code());
		res.setRegionName(data.getRegion_name());
		res.setType(data.getType());
		res.setZip(data.getZip());
		return res;
	}

	public static List<Term> convertFromApi(TermResult apiRes) {
		LOGGER.debug("converting terms from {}", apiRes);

		List<primemover.api.model.Term> inItems = apiRes.getData();
		if (inItems == null || inItems.size() == 0) {
			return new ArrayList<Term>();
		}
		ArrayList<Term> res = new ArrayList<Term>(inItems.size());

		for (primemover.api.model.Term inItem : inItems) {
			Term outItem = new Term();
			outItem.setBigram(inItem.getBigram());
			outItem.setBigramStem(inItem.getBigram_stem());
			outItem.setId(inItem.getId());
			outItem.setRefTerm(inItem.getRefterm());
			outItem.setSearchTerm(inItem.getSearch_term());

			res.add(outItem);
		}

		return res;
	}

	public static List<Outlet> convertFromApi(OutletResult apiRes) {
		LOGGER.debug("converting outlets from {}", apiRes);

		List<primemover.api.model.Outlet> inItems = apiRes.getData();
		ArrayList<Outlet> res = new ArrayList<Outlet>(inItems.size());

		for (primemover.api.model.Outlet inItem : inItems) {
			Outlet outItem = new Outlet();

			outItem.setCreatedAt(inItem.getCreated_at());
			outItem.setDomain(inItem.getDomain());
			outItem.setId(inItem.getId());
			outItem.setOutlet(inItem.getOutlet());
			outItem.setPi(inItem.getPi());
			outItem.setRedirectUrl(inItem.getRedirect_url());
			outItem.setTitle(inItem.getTitle());
			outItem.setUpdatedAt(inItem.getUpdated_at());
			outItem.setUrl(inItem.getUrl());
			outItem.setYhat_n(inItem.getYhat_n());
			outItem.setAvgReachPermillion(inItem.getAvg_reach_permillion());
			outItem.setAvgReachRank(inItem.getAvg_reach_rank());

			res.add(outItem);

		}

		return res;
	}

	public static List<Pair<Crawler, Joblist>> convertFromApi(List<primemover.api.model.Crawler> crawlers,
			CaptchaSolvingType defaultCaptchaSolving) {
		List<Pair<Crawler, Joblist>> out = new ArrayList<>();

		LOGGER.debug("start converting {} items", crawlers.size());

		for (primemover.api.model.Crawler crawler : crawlers) {
			LOGGER.debug("converting crawler id {} / {}", crawler.getId(), crawler.getName());
			List<Pair<Crawler, Joblist>> conversion = convertFromApi(crawler, defaultCaptchaSolving);
			out.addAll(conversion);
		}

		return out;
	}

}
