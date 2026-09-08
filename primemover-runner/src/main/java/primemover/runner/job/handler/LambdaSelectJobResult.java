package primemover.runner.job.handler;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The decision function's answer to one {@code LambdaSelectJob}.
 *
 * <p>
 * Until 2026-08-27 this class held only {@code xpath} and {@code selected}, and
 * {@code @JsonIgnoreProperties(ignoreUnknown = true)} threw the rest of the
 * payload away. That made a degraded decision indistinguishable from a clean
 * one: when a <em>requested</em> LLM filter failed and the video was therefore
 * picked without it, the response still carried an xpath, the click still
 * succeeded and the job still reported {@code success}. A crawler in a treatment
 * arm silently produced control-arm data, and nothing in the runner, the API or
 * the GUI said so. For an experiment platform that is a data-validity problem,
 * not merely a missing log line.
 *
 * <p>
 * The degradation report is therefore parsed here and acted on in
 * {@link LambdaSelectJobHandler}: {@code degraded}, {@code treatment_lost},
 * {@code selection_mode}, {@code filters}, {@code llm}, plus the
 * {@code warnings} list the decision function has always returned and nothing
 * has ever read. The same fields are documented for the whole ecosystem in
 * {@code primemover-orchestrator/orchestrator/status.py}.
 *
 * <p>
 * <b>Which field means what</b>, because getting this wrong is what the amended
 * contract exists to prevent:
 * <ul>
 * <li>{@code degraded} -- a human should look at this decision.</li>
 * <li>{@code treatment_lost} -- this session's data is scientifically void;
 * this, and only this, is an alarm.</li>
 * <li>{@code selection_mode} -- a neutral description of how the pick was made.
 * {@code "random"} is the correct, expected answer for a control crawler, and
 * <b>never</b> an error signal on its own.</li>
 * <li>{@code filters} -- {@code "skipped"} means <em>not requested</em>, which
 * is not a failure.</li>
 * </ul>
 *
 * <p>
 * <b>Every new field is optional.</b> {@code ignoreUnknown} stays on so a
 * <em>newer</em> decision function still parses, and each accessor is null-safe
 * so an <em>older</em> one -- a deployment that predates the degradation report,
 * including whatever image is currently in ECR for {@code lambda.mode=aws} --
 * reads as "nothing to report" rather than as a degradation.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LambdaSelectJobResult {

	/** Value prefix used by the decision function for a filter that raised. */
	private static final String FILTER_FAILED_PREFIX = "failed";

	/** Value used by the decision function for a filter that applied cleanly. */
	private static final String FILTER_OK = "ok";

	/**
	 * Value used by the decision function for a filter that was <em>not
	 * requested</em>. It is not a failure -- five of the six filters are
	 * {@code skipped} on every healthy control-crawler invocation.
	 */
	private static final String FILTER_SKIPPED = "skipped";

	/**
	 * Filters that are <em>always-on hygiene</em> rather than something a
	 * crawler's configuration can ask for, and which therefore can never be part
	 * of "the treatments this crawler requested".
	 *
	 * <p>
	 * This mirrors the decision function's own {@code requested_treatments} map
	 * ({@code bot-decision-yt/bot-decision-EN/lambda_function.py}), which is
	 * initialised for all six filter names and only ever set true for
	 * {@code mainstream_media}, {@code children_entertainment}, {@code similar},
	 * {@code political} and {@code fringe}. Its own comment says why:
	 * "already_watched is deliberately absent: it is always-on hygiene ... not a
	 * configured treatment". That set is the denominator behind
	 * {@code treatment_lost}, so anything the runner derives has to use the same
	 * one or the two will disagree about whether a session was void.
	 */
	private static final Set<String> NON_TREATMENT_FILTERS = Set.of("already_watched");

	private String xpath;
	private Selected selected;

	/** Free-text warnings, one per {@code warnings.warn()} in the decision function. */
	private List<String> warnings;

	/**
	 * True when at least one <em>requested</em> filter failed to apply, or when the
	 * decision function could not select a video at all. A control crawler that ran
	 * perfectly is <b>not</b> degraded, even though it selects at random.
	 */
	private Boolean degraded;

	/**
	 * True when the decision degraded <em>and</em> no requested treatment applied,
	 * i.e. a bot configured to be treated produced control-arm data. This is the
	 * alarm condition; it is computed by the decision function so that no consumer
	 * has to get the two-term gate right on its own. Always false for a control
	 * crawler.
	 */
	@JsonProperty("treatment_lost")
	private Boolean treatmentLost;

	/**
	 * {@code "llm"} or {@code "random"}. A <b>neutral description</b> of how the
	 * video was chosen: {@code "random"} means no LLM rating informed the pick.
	 * That is the expected, correct answer for a control crawler
	 * (pi=psi=kappa=tau=0), so it is not on its own an error signal and nothing may
	 * branch on it alone. Use {@link #isTreatmentLost()} for the alarm and
	 * {@link #isDegraded()} for "a human should look at this".
	 */
	@JsonProperty("selection_mode")
	private String selectionMode;

	/**
	 * Per-filter outcome: {@code "ok"}, {@code "skipped"} or
	 * {@code "failed: <ErrType>: <msg>"}.
	 */
	private Map<String, String> filters;

	/** {@code {available: bool, error_type: str|null, error: str|null}}. */
	private Map<String, Object> llm;

	public LambdaSelectJobResult() {
	}

	public String getXpath() {
		return xpath;
	}

	public void setXpath(String xpath) {
		this.xpath = xpath;
	}

	public Selected getSelected() {
		return selected;
	}

	public void setSelected(Selected selected) {
		this.selected = selected;
	}

	/** Never {@code null}; empty when the payload carried no warnings. */
	public List<String> getWarnings() {
		return warnings == null ? Collections.emptyList() : Collections.unmodifiableList(warnings);
	}

	public void setWarnings(List<String> warnings) {
		this.warnings = warnings;
	}

	/**
	 * {@code true} only when the payload explicitly says the decision degraded. A
	 * payload that predates the flag reads as {@code false} -- absence is not
	 * evidence of degradation, and treating it as such would turn every job run
	 * against an older decision function into a warning.
	 */
	public boolean isDegraded() {
		return Boolean.TRUE.equals(degraded);
	}

	public void setDegraded(Boolean degraded) {
		this.degraded = degraded;
	}

	/**
	 * {@code true} when this job's data is scientifically void: the crawler asked
	 * for a treatment, the treatment did not apply, and the session therefore
	 * produced control-arm data. <b>This is the flag to alert on</b>, and the only
	 * one.
	 *
	 * <p>
	 * The decision function computes it (degraded AND no requested treatment
	 * applied) precisely so that every consumer -- runner, API, GUI, orchestrator
	 * -- gates on one boolean instead of re-deriving a two-term rule and getting it
	 * wrong. When the flag is <em>absent</em> the payload predates it, and only
	 * then is the two-term rule recomputed here from {@code degraded} and
	 * {@code selection_mode}. A payload with no degradation report at all reads as
	 * "unknown", i.e. {@code false}: absence must never be read as an alarm.
	 */
	public boolean isTreatmentLost() {
		if (treatmentLost != null) {
			return treatmentLost;
		}

		// legacy payload: fall back to the gate the source now computes for us
		return isDegraded() && isRandomSelection();
	}

	public void setTreatmentLost(Boolean treatmentLost) {
		this.treatmentLost = treatmentLost;
	}

	/**
	 * {@code true} when the payload carried the explicit {@code treatment_lost}
	 * flag, as opposed to it having been recomputed from the older two fields.
	 */
	@JsonIgnore
	public boolean hasTreatmentLostFlag() {
		return treatmentLost != null;
	}

	/** The raw mode, or {@code null} when the payload predates the field. */
	public String getSelectionMode() {
		return selectionMode;
	}

	public void setSelectionMode(String selectionMode) {
		this.selectionMode = selectionMode;
	}

	/** Never {@code null}. */
	public Map<String, String> getFilters() {
		return filters == null ? Collections.emptyMap() : Collections.unmodifiableMap(filters);
	}

	public void setFilters(Map<String, String> filters) {
		this.filters = filters;
	}

	/** Never {@code null}. */
	public Map<String, Object> getLlm() {
		return llm == null ? Collections.emptyMap() : Collections.unmodifiableMap(llm);
	}

	public void setLlm(Map<String, Object> llm) {
		this.llm = llm;
	}

	/**
	 * {@code true} when the video was picked without any LLM rating informing the
	 * choice.
	 *
	 * <p>
	 * <b>Descriptive, not diagnostic.</b> 82% of this host's recorded decisions
	 * (295 of 358) are exactly this, because a control crawler requests no rating
	 * filter and is therefore <em>supposed</em> to draw uniformly. Nothing may
	 * branch on this alone; it exists to describe a decision in a log line and to
	 * feed the legacy fallback in {@link #isTreatmentLost()}.
	 *
	 * <p>
	 * Deliberately keyed on the explicit {@code selection_mode} only. An empty
	 * {@code filters} map or a missing {@code llm} block is what an older payload
	 * looks like, and inferring "random" from those would raise a false alarm on
	 * every job.
	 */
	@JsonIgnore
	public boolean isRandomSelection() {
		return "random".equalsIgnoreCase(trimmed(selectionMode));
	}

	/**
	 * {@code true} when the payload carries a degradation report at all, i.e. it
	 * comes from a decision function new enough to have one. Used to tell "clean"
	 * apart from "cannot tell".
	 */
	@JsonIgnore
	public boolean hasDegradationReport() {
		return degraded != null || treatmentLost != null || selectionMode != null || filters != null || llm != null;
	}

	/**
	 * The filters that raised, as {@code "<name> <status>"} pairs in payload order,
	 * e.g. {@code "similar failed: RateLimitError: Rate limit reached"}. Filters
	 * that applied ({@code ok}) or were never requested ({@code skipped}) are not
	 * failures and are left out.
	 */
	@JsonIgnore
	public List<String> failedFilters() {
		if (filters == null || filters.isEmpty()) {
			return Collections.emptyList();
		}

		List<String> out = new ArrayList<>();
		for (Map.Entry<String, String> entry : filters.entrySet()) {
			String status = trimmed(entry.getValue());
			if (status != null && status.toLowerCase().startsWith(FILTER_FAILED_PREFIX)) {
				out.add(entry.getKey() + " " + status);
			}
		}

		return out;
	}

	/**
	 * The <em>configured treatments</em> this crawler asked for, by name.
	 *
	 * <p>
	 * This is the denominator the whole degradation contract is written against.
	 * "Degraded" means a <b>requested</b> filter did not apply, and
	 * "treatment lost" means none of the requested ones did; a control crawler
	 * requests nothing, so it can lose nothing however it picked its video. Reading
	 * a non-request as a request is the single most common way to turn this
	 * contract back into a false-alarm generator -- see
	 * {@code docs/integration-contract.md} &sect;7.3 -- and a <b>false</b>
	 * treatment_lost is as damaging as a missed one: it voids sessions that were
	 * fine.
	 *
	 * <p>
	 * The decision function does not (yet) put its own {@code requested} set on the
	 * wire, so it is reconstructed here from {@code filters}, and the
	 * reconstruction has to match {@code lambda_function.py}'s
	 * {@code requested_treatments} exactly. Two rules do that:
	 *
	 * <ul>
	 * <li><b>Only a configured treatment counts</b> -- see
	 * {@link #NON_TREATMENT_FILTERS}. {@code already_watched} is always-on hygiene
	 * and is never in the function's request set, so it is never in this one.</li>
	 * <li><b>Only a state that evidences a request counts</b>: {@code "ok"} or
	 * {@code "failed: ..."}, the only two states the function gives a filter it was
	 * asked to apply. Everything else -- {@code "skipped"} (not requested),
	 * {@code "not_implemented"} (the state the deployed function writes for
	 * {@code already_watched} on <em>every single invocation</em>), a blank state,
	 * or any state a future version invents -- says nothing about whether the
	 * filter was requested, and unknown is never a request.</li>
	 * </ul>
	 *
	 * <p>
	 * Inferring "requested" from "not skipped" instead put
	 * {@code requested = [already_watched]}, {@code applied = []} on every healthy
	 * control job -- about 82% of the lambdaselect jobs on this box -- which reads,
	 * in the GUI and in any SQL that computes casualties as
	 * {@code requested - applied}, as exactly the "a requested treatment did not
	 * apply" verdict {@code treatment_lost} exists to stop anyone re-deriving.
	 */
	@JsonIgnore
	public List<String> requestedFilters() {
		if (filters == null || filters.isEmpty()) {
			return Collections.emptyList();
		}

		List<String> out = new ArrayList<>();
		for (Map.Entry<String, String> entry : filters.entrySet()) {
			if (!isConfiguredTreatment(entry.getKey())) {
				continue;
			}
			if (!statesTheFilterWasRequested(trimmed(entry.getValue()))) {
				continue;
			}
			out.add(entry.getKey());
		}

		return out;
	}

	/**
	 * The requested treatments that applied cleanly, by name.
	 *
	 * <p>
	 * Kept over the same denominator as {@link #requestedFilters()} on purpose:
	 * the two travel together in {@code decision_report} and are read as a pair,
	 * and the decision function defines its own {@code applied} as
	 * {@code {name in requested : filters[name] == 'ok'}}. An always-on hygiene
	 * filter reported {@code ok} is not a treatment that applied, and letting it
	 * into this list would let a consumer that asks "did any treatment apply?"
	 * answer yes about a session in which none did.
	 */
	@JsonIgnore
	public List<String> appliedFilters() {
		if (filters == null || filters.isEmpty()) {
			return Collections.emptyList();
		}

		List<String> out = new ArrayList<>();
		for (Map.Entry<String, String> entry : filters.entrySet()) {
			if (!isConfiguredTreatment(entry.getKey())) {
				continue;
			}
			if (FILTER_OK.equalsIgnoreCase(trimmed(entry.getValue()))) {
				out.add(entry.getKey());
			}
		}

		return out;
	}

	/**
	 * {@code true} when {@code name} is a filter a crawler's configuration can ask
	 * for, as opposed to always-on hygiene the decision function applies to every
	 * invocation. Unknown names are treated as treatments so that a filter added
	 * to the contract later is counted rather than silently dropped.
	 */
	private static boolean isConfiguredTreatment(String name) {
		String trimmed = trimmed(name);
		return trimmed != null && !NON_TREATMENT_FILTERS.contains(trimmed.toLowerCase(Locale.ROOT));
	}

	/**
	 * {@code true} when a filter's state is evidence that the filter was actually
	 * requested: it either applied ({@code "ok"}) or was attempted and raised
	 * ({@code "failed: ..."}). Those are the only two states the decision function
	 * leaves on a filter it was asked for; anything else is not a request.
	 */
	private static boolean statesTheFilterWasRequested(String status) {
		if (status == null || FILTER_SKIPPED.equalsIgnoreCase(status)) {
			// "skipped" is the contract's "never requested", and the normal state of
			// five of the six filters on every healthy control invocation. Spelled out
			// rather than left to the whitelist below because misreading it as a
			// casualty is the mistake this method exists to prevent.
			return false;
		}

		String lower = status.toLowerCase(Locale.ROOT);
		return FILTER_OK.equals(lower) || lower.startsWith(FILTER_FAILED_PREFIX);
	}

	/**
	 * {@code true} unless the {@code llm} block explicitly says the LLM was
	 * unavailable. A missing block means "not reported", which must not read as an
	 * outage.
	 */
	@JsonIgnore
	public boolean isLlmAvailable() {
		if (llm == null) {
			return true;
		}

		Object available = llm.get("available");
		if (available == null) {
			return true;
		}
		if (available instanceof Boolean) {
			return (Boolean) available;
		}

		return !"false".equalsIgnoreCase(String.valueOf(available).trim());
	}

	/**
	 * The classified LLM error type ({@code rate_limit}, {@code timeout}, ...), or
	 * {@code null}.
	 */
	@JsonIgnore
	public String llmErrorType() {
		return llmString("error_type");
	}

	/** The LLM error message, or {@code null}. */
	@JsonIgnore
	public String llmError() {
		return llmString("error");
	}

	/**
	 * A single-line rendering of the {@code llm} block for a log line or a
	 * {@code status_message} detail, e.g. {@code "rate_limit: Rate limit reached"};
	 * {@code null} when the LLM reported no problem.
	 */
	@JsonIgnore
	public String describeLlmProblem() {
		String type = llmErrorType();
		String message = llmError();

		if (type == null && message == null) {
			return isLlmAvailable() ? null : "llm unavailable";
		}
		if (type == null) {
			return message;
		}
		if (message == null) {
			return type;
		}

		return type + ": " + message;
	}

	/**
	 * Defensive copy for callers that want to log the whole report; keeps the order
	 * the decision function used.
	 */
	@JsonIgnore
	public Map<String, String> filtersSnapshot() {
		return filters == null ? Collections.emptyMap() : new LinkedHashMap<>(filters);
	}

	private String llmString(String key) {
		if (llm == null) {
			return null;
		}

		Object value = llm.get(key);
		if (value == null) {
			return null;
		}

		return trimmed(String.valueOf(value));
	}

	private static String trimmed(String value) {
		if (value == null) {
			return null;
		}

		String stripped = value.strip();
		return stripped.isEmpty() ? null : stripped;
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Selected {
		private String id;
		private String title;

		public Selected() {
		}

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public String getTitle() {
			return title;
		}

		public void setTitle(String title) {
			this.title = title;
		}
	}

}
