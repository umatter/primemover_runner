package primemover.runner;

import org.json.JSONArray;
import org.json.JSONObject;

import primemover.runner.job.handler.LambdaSelectJobResult;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One decision function verdict, in the shape the API stores it.
 *
 * <p>
 * Until now the verdict {@link LambdaSelectJobResult} already parses -- did a
 * requested filter fail, was the treatment lost, which filter, why -- reached
 * the runner log and the MinIO job-data object, and the API only as prose inside
 * {@code status_message}. Prose is not queryable: "which of last night's jobs
 * produced control-arm data despite being assigned a treatment" was a question
 * nobody could answer with SQL. This value object is what
 * {@link primemover.service.JobService} puts on the job PUT so that it becomes
 * four columns on the job row.
 *
 * <p>
 * <b>Absence is the point.</b> {@link #of(LambdaSelectJobResult)} returns
 * {@code null} for a payload that carries no degradation report, and the service
 * then omits all four keys. A missing key means <em>unknown</em> on the API side,
 * which is exactly right for a legacy payload, a non-lambda job, or a decision
 * function that predates degradation reporting. Sending {@code false} there would
 * assert something nobody measured -- the single mistake this whole contract
 * (docs/integration-contract.md &sect;7.3) exists to prevent. A verdict object
 * therefore always carries a real report; there is no "empty" instance.
 *
 * <p>
 * <b>Which field means what</b> (do not re-derive these, they are normative):
 * <ul>
 * <li>{@code degraded} -- a <em>requested</em> filter failed to apply, or
 * nothing could be selected. "A human should look at this decision."</li>
 * <li>{@code treatment_lost} -- degraded <em>and</em> nothing requested applied:
 * a bot configured to be treated produced control-arm data. The alarm.</li>
 * <li>{@code selection_mode} -- description only. {@code "random"} is the
 * correct, expected answer for a control crawler and <b>must never</b> be
 * branched on alone.</li>
 * <li>{@code filters} -- {@code "skipped"} means <em>not requested</em>, never a
 * failure; that is why {@link #requested()} excludes it.</li>
 * </ul>
 *
 * <p>
 * <b>Everything here is bounded.</b> The report travels in an HTTP PUT body that
 * a browser-driven job produces without supervision, and the decision function's
 * {@code warnings[]} is an unbounded list of free text it builds in a loop. Each
 * piece is capped on construction, and {@link #reportJson()} applies a final
 * whole-object guard, so a pathological payload cannot blow up the request. The
 * unabridged report stays in the runner log and in the recorded MinIO object.
 */
public final class DecisionVerdict {

	/**
	 * How many of the decision function's own free-text warnings travel to the
	 * API. Deliberately the same bound
	 * ({@code LambdaSelectJobHandler.MAX_LAMBDA_WARNINGS_REPORTED}) that the
	 * status_message detail already uses: the two renderings of the same
	 * degradation should not disagree about how much of it they show. When more
	 * were dropped, an overflow note is appended so the reader knows to go to the
	 * recorded payload.
	 */
	public static final int MAX_REPORT_WARNINGS = 4;

	/** Longest single warning kept. */
	public static final int MAX_WARNING_CHARS = 200;

	/** Six filters are contractual; the headroom is for a future one. */
	public static final int MAX_FILTERS = 12;

	public static final int MAX_FILTER_NAME_CHARS = 48;

	/** {@code "failed: <ExcClass>: <msg>"} needs room; a stack trace does not. */
	public static final int MAX_FILTER_STATUS_CHARS = 160;

	/** Entries kept in {@code applied} / {@code requested}. */
	public static final int MAX_FILTER_NAMES_LISTED = 12;

	/** The API column is {@code varchar(32)}. */
	public static final int MAX_SELECTION_MODE_CHARS = 32;

	public static final int MAX_LLM_FIELD_CHARS = 200;

	/**
	 * Whole-object backstop on the serialized {@code decision_report}. The
	 * per-element caps above already bound the worst case well below this; the
	 * guard exists so that a payload shape nobody anticipated still cannot put an
	 * unbounded body on the wire.
	 */
	public static final int MAX_REPORT_JSON_CHARS = 6000;

	/**
	 * Keys dropped, least important first, if the guard above ever fires.
	 * {@code filters} is last because it is the field that says <em>which</em>
	 * part of the treatment was lost.
	 */
	private static final String[] DROP_ORDER = { "warnings", "requested", "applied", "llm", "filters" };

	private static final String TRUNCATION_MARKER = "...";

	private final boolean degraded;
	private final boolean treatmentLost;
	/**
	 * jobs.decision_selected_id is varchar(64); a YouTube id is 11.
	 */
	private static final int MAX_SELECTED_ID_CHARS = 64;

	private final @Nullable String selectionMode;

	/**
	 * The video this decision chose, or null when the payload named none.
	 *
	 * Carried on the same PUT as the verdict so a bot's history is current the
	 * moment a job ends. It used to reach the database only via a backfill out
	 * of MinIO, which meant the already_watched filter could not see a choice
	 * the same session had just made -- a bot could re-watch, minutes later, a
	 * video it had picked itself.
	 *
	 * Null is unknown, never "chose nothing"; a decision that genuinely
	 * selected nothing is already reported by degraded with an empty xpath.
	 */
	private final @Nullable String selectedId;
	private final Map<String, String> filters;
	private final Map<String, Object> llm;
	private final List<String> warnings;
	private final List<String> applied;
	private final List<String> requested;

	private DecisionVerdict(boolean degraded, boolean treatmentLost, @Nullable String selectionMode,
			@Nullable String selectedId,
			Map<String, String> filters, Map<String, Object> llm, List<String> warnings, List<String> applied,
			List<String> requested) {
		this.degraded = degraded;
		this.treatmentLost = treatmentLost;
		this.selectionMode = selectionMode;
		this.selectedId = selectedId;
		this.filters = Collections.unmodifiableMap(filters);
		this.llm = Collections.unmodifiableMap(llm);
		this.warnings = Collections.unmodifiableList(warnings);
		this.applied = Collections.unmodifiableList(applied);
		this.requested = Collections.unmodifiableList(requested);
	}

	/**
	 * The verdict carried by {@code parsed}, or {@code null} when the payload
	 * carries no degradation report at all.
	 *
	 * <p>
	 * {@code null} is not "nothing went wrong" -- it is "we cannot tell", and the
	 * caller must send nothing rather than {@code false}. See the class comment.
	 */
	@Nullable
	public static DecisionVerdict of(@Nullable LambdaSelectJobResult parsed) {
		if (parsed == null || !parsed.hasDegradationReport()) {
			return null;
		}

		return new DecisionVerdict(parsed.isDegraded(), parsed.isTreatmentLost(),
				shorten(parsed.getSelectionMode(), MAX_SELECTION_MODE_CHARS), selectedIdOf(parsed),
				boundedFilters(parsed.filtersSnapshot()),
				boundedLlm(parsed), boundedWarnings(parsed.getWarnings()),
				boundedNames(parsed.appliedFilters()), boundedNames(parsed.requestedFilters()));
	}

	/** "A human should look at this decision." */
	public boolean isDegraded() {
		return degraded;
	}

	/**
	 * "This session's data is scientifically void": the bot was configured to be
	 * treated and produced control-arm data. The one alarm condition.
	 */
	public boolean isTreatmentLost() {
		return treatmentLost;
	}

	/**
	 * {@code "llm"} / {@code "random"}, or {@code null} when the report did not
	 * say. Descriptive only -- never a gate.
	 */
	@Nullable
	public String getSelectionMode() {
		return selectionMode;
	}

	public @Nullable String getSelectedId() {
		return selectedId;
	}

	/**
	 * The chosen video's id, bounded to what the column holds, or null.
	 *
	 * Blank is null: an empty string in a history would match nothing and read
	 * as a real answer, which is worse than admitting we do not know.
	 */
	private static @Nullable String selectedIdOf(LambdaSelectJobResult parsed) {
		LambdaSelectJobResult.Selected selected = parsed.getSelected();

		if (selected == null || selected.getId() == null) {
			return null;
		}

		String id = selected.getId().trim();

		return id.isEmpty() ? null : shorten(id, MAX_SELECTED_ID_CHARS);
	}

	/** Bounded per-filter report, {@code name -> "ok" | "skipped" | "failed: ..."}. */
	public Map<String, String> filters() {
		return filters;
	}

	/** Bounded {@code {available, error_type, error}}; only keys the payload had. */
	public Map<String, Object> llm() {
		return llm;
	}

	/** Bounded copy of the decision function's own free-text warnings. */
	public List<String> warnings() {
		return warnings;
	}

	/** Requested treatments that applied cleanly. Always a subset of {@link #requested()}. */
	public List<String> applied() {
		return applied;
	}

	/**
	 * The configured treatments the crawler actually asked for: a filter that is
	 * not always-on hygiene and whose state says it was applied ({@code "ok"}) or
	 * attempted and raised ({@code "failed: ..."}).
	 *
	 * <p>
	 * Persisted because "degraded" only means something against what was
	 * requested: a control crawler requests nothing and can therefore lose
	 * nothing. Every other state -- {@code "skipped"}, {@code "not_implemented"},
	 * blank, or anything a later decision function invents -- says nothing about
	 * whether the filter was asked for, and unknown is never a request. See
	 * {@link LambdaSelectJobResult#requestedFilters()}: inflating this list is how
	 * a healthy control job comes to read as a lost treatment.
	 */
	public List<String> requested() {
		return requested;
	}

	/**
	 * Worst-first rank used to keep the more serious of two verdicts when a job
	 * invokes the decision function more than once (the handler re-invokes on a
	 * click retry). A job whose first decision lost the treatment must not be
	 * recorded as clean because a later retry happened to be fine -- the recorded
	 * session already contains the degraded decision.
	 */
	int severity() {
		if (treatmentLost) {
			return 2;
		}
		return degraded ? 1 : 0;
	}

	/**
	 * The {@code decision_report} object as it goes on the wire:
	 * {@code {filters, llm, warnings, applied, requested}}.
	 *
	 * <p>
	 * A fresh object each call, so a caller cannot mutate the verdict through it.
	 */
	public JSONObject reportJson() {
		JSONObject report = new JSONObject();
		report.put("filters", new JSONObject(filters));
		report.put("llm", new JSONObject(llm));
		report.put("warnings", new JSONArray(warnings));
		report.put("applied", new JSONArray(applied));
		report.put("requested", new JSONArray(requested));

		if (report.toString().length() <= MAX_REPORT_JSON_CHARS) {
			return report;
		}

		// Backstop only; the per-element caps should have made this unreachable.
		for (String key : DROP_ORDER) {
			report.remove(key);
			report.put("truncated", true);
			if (report.toString().length() <= MAX_REPORT_JSON_CHARS) {
				break;
			}
		}

		return report;
	}

	@Override
	public String toString() {
		return "DecisionVerdict[degraded=" + degraded + ", treatmentLost=" + treatmentLost + ", selectionMode="
				+ selectionMode + ", filters=" + filters + "]";
	}

	private static Map<String, String> boundedFilters(Map<String, String> raw) {
		Map<String, String> out = new LinkedHashMap<>();
		if (raw == null) {
			return out;
		}

		for (Map.Entry<String, String> entry : raw.entrySet()) {
			if (out.size() >= MAX_FILTERS) {
				break;
			}
			String name = shorten(entry.getKey(), MAX_FILTER_NAME_CHARS);
			if (name == null) {
				continue;
			}
			String status = shorten(entry.getValue(), MAX_FILTER_STATUS_CHARS);
			out.put(name, status == null ? "" : status);
		}

		return out;
	}

	/**
	 * Only the keys the payload actually carried.
	 *
	 * <p>
	 * {@code LambdaSelectJobResult.isLlmAvailable()} answers {@code true} for a
	 * missing {@code llm} block, which is the right default for a log line but the
	 * wrong thing to persist: it would record "the LLM was available" about a
	 * payload that never said so. Absence stays absence here.
	 */
	private static Map<String, Object> boundedLlm(LambdaSelectJobResult parsed) {
		Map<String, Object> out = new LinkedHashMap<>();
		Map<String, Object> raw = parsed.getLlm();
		if (raw.isEmpty()) {
			return out;
		}

		if (raw.containsKey("available")) {
			out.put("available", parsed.isLlmAvailable());
		}

		String errorType = shorten(parsed.llmErrorType(), MAX_LLM_FIELD_CHARS);
		if (errorType != null) {
			out.put("error_type", errorType);
		}

		String error = shorten(parsed.llmError(), MAX_LLM_FIELD_CHARS);
		if (error != null) {
			out.put("error", error);
		}

		return out;
	}

	private static List<String> boundedWarnings(List<String> raw) {
		List<String> out = new ArrayList<>();
		if (raw == null || raw.isEmpty()) {
			return out;
		}

		int kept = 0;
		for (String warning : raw) {
			if (kept >= MAX_REPORT_WARNINGS) {
				out.add("(+" + (raw.size() - kept) + " more warnings, see the recorded lambda payload)");
				break;
			}
			String text = shorten(warning, MAX_WARNING_CHARS);
			if (text != null) {
				out.add(text);
				kept++;
			}
		}

		return out;
	}

	private static List<String> boundedNames(List<String> raw) {
		List<String> out = new ArrayList<>();
		if (raw == null) {
			return out;
		}

		for (String name : raw) {
			if (out.size() >= MAX_FILTER_NAMES_LISTED) {
				break;
			}
			String text = shorten(name, MAX_FILTER_NAME_CHARS);
			if (text != null) {
				out.add(text);
			}
		}

		return out;
	}

	/**
	 * Collapse to a single truncated line. The same treatment
	 * {@code JobService.buildStatusMessage} gives the status detail, for the same
	 * reason: this text is read in table cells and log lines, and a decision
	 * function warning can contain an embedded traceback.
	 */
	@Nullable
	private static String shorten(@Nullable String value, int limit) {
		if (value == null) {
			return null;
		}

		String text = value.replaceAll("\\s+", " ").strip();
		if (text.isEmpty()) {
			return null;
		}

		if (text.length() <= limit) {
			return text;
		}

		return text.substring(0, cutPoint(text, limit - TRUNCATION_MARKER.length())) + TRUNCATION_MARKER;
	}

	/**
	 * The largest cut at or below {@code limit} that does not split a surrogate
	 * pair. A video title arriving from YouTube inside a filter's failure message
	 * is attacker-supplied text, and a lone surrogate has no UTF-8 encoding: the
	 * API would store {@code ?}. Same guard as
	 * {@code JobService.buildStatusMessage}.
	 */
	private static int cutPoint(String text, int limit) {
		if (limit <= 0) {
			return 0;
		}
		if (limit >= text.length()) {
			return text.length();
		}
		if (Character.isHighSurrogate(text.charAt(limit - 1)) && Character.isLowSurrogate(text.charAt(limit))) {
			return limit - 1;
		}
		return limit;
	}
}
