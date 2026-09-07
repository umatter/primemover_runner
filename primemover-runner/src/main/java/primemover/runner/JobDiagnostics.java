package primemover.runner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Per-job diagnostics collected while a job handler runs.
 *
 * <p>
 * Two independent things live here, both of which exist so that a diagnostic
 * survives the trip from the code that noticed it to the {@code status_message}
 * the API stores:
 *
 * <ul>
 * <li><b>Warnings.</b> A job handler that completes its work but notices that
 * something the <em>experiment</em> cares about degraded (most importantly: the
 * decision function reported a degraded decision -- in the worst case treatment_lost,
 * silently turns a treatment arm into a control arm) calls
 * {@link #addWarning(String)}. {@link TaskOperator} reads them after a
 * successful handler run and finishes the job as
 * {@link primemover.model.JobState#WARNING} instead of {@code SUCCESS}, with the
 * warnings as the {@code status_message} detail.</li>
 * <li><b>The decision verdict.</b> {@code LambdaSelectJobHandler} records the
 * parsed {@link DecisionVerdict} for the job it is running, and
 * {@link TaskOperator} hands it to the service so it becomes four columns on the
 * job row rather than prose inside {@code status_message}. It travels the same
 * per-job path as the warnings above, and for the same reason: a job handler has
 * no other way to reach the code that reports the job's outcome.</li>
 * <li><b>{@link #describe(Throwable)}.</b> The single-line rendering of an
 * exception that is safe to send to the API -- class name plus message plus the
 * root cause when it differs, and never a stack trace.</li>
 * <li><b>{@link #correlationId(Long, Long, Long, Long)}.</b> The one place the
 * hierarchical correlation id put into the MDC is spelled out.</li>
 * </ul>
 *
 * <p>
 * <b>Thread safety.</b> The runner executes up to
 * {@code runner.MaxParallelExecution} joblists concurrently on a pooled
 * executor, and a job handler runs on the same thread as the
 * {@link TaskOperator} that invoked it. Warnings are therefore held in a
 * {@link ThreadLocal}, so two concurrent jobs can never see each other's
 * warnings, and the list itself is copy-on-write so that a helper thread
 * sharing the collection cannot corrupt it. {@link #beginJob()} clears any
 * residue before a job starts and {@link #endJob()} removes the entry entirely
 * afterwards, so nothing leaks between two jobs that happen to reuse the same
 * pooled thread.
 *
 * <p>
 * Warnings added outside a job (i.e. before {@link #beginJob()}) are not
 * attributed to any job: the next {@code beginJob()} discards them.
 */
public final class JobDiagnostics {

	private static final Logger logger = LoggerFactory.getLogger(JobDiagnostics.class);

	/**
	 * Upper bound on the warnings kept for one job. The status_message is
	 * truncated by the service anyway; this stops a handler in a loop from
	 * growing the list without bound.
	 */
	static final int MAX_WARNINGS = 20;

	private static final ThreadLocal<CopyOnWriteArrayList<String>> WARNINGS = new ThreadLocal<>();

	/**
	 * The decision-function verdict for the job on this thread, if it had one.
	 * Scoped exactly like {@link #WARNINGS}: set by the lambda-select handler,
	 * read by {@link TaskOperator} when it reports the job's terminal state,
	 * cleared at both ends of the job so a pooled thread cannot carry one job's
	 * verdict into the next.
	 */
	private static final ThreadLocal<DecisionVerdict> DECISION = new ThreadLocal<>();

	private JobDiagnostics() {
		// static utility
	}

	/**
	 * Attach a warning to the job currently executing on this thread.
	 *
	 * <p>
	 * Call this from a job handler when the job did its work but the result is
	 * degraded in a way the experiment should know about. The job will be
	 * reported to the API as {@code warning} rather than {@code success}, with
	 * this text (joined with any other warnings by {@code "; "}) as the
	 * {@code status_message} detail.
	 *
	 * <p>
	 * Keep the text short, single-line and factual, e.g.
	 * {@code "lambda selection_mode=random (llm unavailable: RateLimitError)"}.
	 * Blank texts are ignored.
	 *
	 * @param warning the warning text; {@code null} or blank is ignored
	 */
	public static void addWarning(@Nullable String warning) {
		if (warning == null || warning.isBlank()) {
			return;
		}

		CopyOnWriteArrayList<String> list = WARNINGS.get();
		if (list == null) {
			list = new CopyOnWriteArrayList<>();
			WARNINGS.set(list);
		}

		if (list.size() >= MAX_WARNINGS) {
			logger.debug("dropping job warning, already at {} warnings: {}", MAX_WARNINGS, warning);
			return;
		}

		logger.warn("job warning: {}", warning);
		list.add(warning.strip());
	}

	/**
	 * Attach several warnings at once. {@code null} entries are ignored.
	 */
	public static void addWarnings(@Nullable Collection<String> warnings) {
		if (warnings == null) {
			return;
		}
		for (String warning : warnings) {
			addWarning(warning);
		}
	}

	/**
	 * The warnings collected for the job currently executing on this thread, in
	 * the order they were added. Never {@code null}; the returned list is an
	 * immutable snapshot.
	 */
	public static List<String> warnings() {
		CopyOnWriteArrayList<String> list = WARNINGS.get();
		if (list == null || list.isEmpty()) {
			return Collections.emptyList();
		}
		return List.copyOf(list);
	}

	/**
	 * Record the decision function's verdict for the job currently executing on
	 * this thread. {@code null} is ignored, so a payload that carried no
	 * degradation report leaves the slot empty -- and the job PUT then omits the
	 * decision fields entirely, which is how the API learns "unknown" rather than
	 * being told a {@code false} nobody measured.
	 *
	 * <p>
	 * A job can invoke the decision function more than once: the handler
	 * re-invokes it when the recommendation it was given could not be clicked.
	 * The <b>more severe</b> verdict wins (treatment lost &gt; degraded &gt;
	 * clean), with a later verdict winning a tie. A job whose first decision lost
	 * the treatment must not end up recorded as clean because a retry happened to
	 * come back fine -- the session already contains that degraded decision, and
	 * its warnings are already on the job's status message.
	 */
	public static void recordDecisionVerdict(@Nullable DecisionVerdict verdict) {
		if (verdict == null) {
			return;
		}

		DecisionVerdict existing = DECISION.get();
		if (existing == null || verdict.severity() >= existing.severity()) {
			DECISION.set(verdict);
		}
	}

	/**
	 * The decision verdict recorded for the job currently executing on this
	 * thread, or {@code null} when there is none.
	 *
	 * <p>
	 * {@code null} means <b>unknown</b>, not clean: a non-lambda job, or a
	 * decision function whose payload predates the degradation report. Callers
	 * must send nothing rather than a default.
	 */
	@Nullable
	public static DecisionVerdict decisionVerdict() {
		return DECISION.get();
	}

	/**
	 * {@code true} when at least one warning has been attached to the job
	 * currently executing on this thread.
	 */
	public static boolean hasWarnings() {
		CopyOnWriteArrayList<String> list = WARNINGS.get();
		return list != null && !list.isEmpty();
	}

	/**
	 * Start collecting warnings and the decision verdict for a job on this
	 * thread, discarding anything left over. Called by {@link TaskOperator} only.
	 */
	static void beginJob() {
		WARNINGS.set(new CopyOnWriteArrayList<>());
		DECISION.remove();
	}

	/**
	 * Stop collecting and return the warnings, removing both thread-locals so
	 * nothing -- warning or verdict -- leaks to the next job on a pooled thread.
	 * Called by {@link TaskOperator} only.
	 */
	static List<String> endJob() {
		List<String> collected = warnings();
		WARNINGS.remove();
		DECISION.remove();
		return collected;
	}

	/**
	 * Render a throwable as a single line that is safe to store as a
	 * {@code status_message} detail: exception class simple name and message,
	 * plus the root cause when it differs from the exception itself.
	 *
	 * <p>
	 * Deliberately never a stack trace -- that stays in the runner log.
	 *
	 * @param t the throwable, may be {@code null}
	 * @return the description, or {@code null} when {@code t} is {@code null}
	 */
	@Nullable
	public static String describe(@Nullable Throwable t) {
		if (t == null) {
			return null;
		}

		StringBuilder out = new StringBuilder(256);
		out.append(render(t));

		Throwable root = rootCause(t);
		if (root != t) {
			String rendered = render(root);
			// only worth appending when it actually says something new
			if (!out.toString().contains(rendered)) {
				out.append(" | caused by: ").append(rendered);
			}
		}

		return out.toString();
	}

	private static String render(Throwable t) {
		String message = t.getMessage();
		if (message == null || message.isBlank()) {
			return t.getClass().getSimpleName();
		}
		// Collapse, do not merely strip. Selenium is the reason: every
		// WebDriverException appends its own "Build info: / System info: / Driver
		// info:" block to getMessage(), so `tab crashed` arrives as NINE lines.
		// strip() only trims the ends, so the result stayed multi-line and the
		// single-line guarantee this method advertises held only by accident --
		// JobService.buildStatusMessage collapses whitespace on the way out, and
		// every current caller happens to go through it. Do it here too, so the
		// guarantee belongs to the method rather than to its callers.
		return t.getClass().getSimpleName() + ": " + message.replaceAll("\\s+", " ").strip();
	}

	/**
	 * Build the hierarchical correlation id used for cross-component tracing.
	 *
	 * <p>
	 * The canonical format, documented in {@code docs/integration-contract.md},
	 * is {@code exp-{experiment}|cr-{crawler}|q-{queue}|j-{job}}. A segment whose
	 * id is unknown is <b>omitted</b>, never filled with a placeholder -- in
	 * particular the runner never learns the experiment id, because the API's
	 * crawler payload does not carry one.
	 *
	 * <p>
	 * The result must contain neither whitespace nor a comma: logback renders the
	 * whole MDC map comma-separated and promtail scrapes the value with
	 * {@code correlationId=(?P<correlation_id>[^\s,]+)}.
	 *
	 * @return the correlation id, or {@code null} when no id at all is known
	 */
	@Nullable
	public static String correlationId(@Nullable Long experimentId, @Nullable Long crawlerId, @Nullable Long queueId,
			@Nullable Long jobId) {
		StringBuilder out = new StringBuilder(48);
		appendSegment(out, "exp-", experimentId);
		appendSegment(out, "cr-", crawlerId);
		appendSegment(out, "q-", queueId);
		appendSegment(out, "j-", jobId);

		return out.length() == 0 ? null : out.toString();
	}

	/**
	 * Append the {@code |j-<id>} segment to an existing correlation id, for the
	 * duration of one job. Returns {@code base} unchanged when {@code jobId} is
	 * unknown, and a bare {@code j-<id>} when there is no outer id to extend.
	 */
	@Nullable
	public static String withJob(@Nullable String base, @Nullable Long jobId) {
		if (jobId == null) {
			return base;
		}
		if (base == null || base.isEmpty()) {
			return "j-" + jobId;
		}
		return base + "|j-" + jobId;
	}

	private static void appendSegment(StringBuilder out, String prefix, @Nullable Long id) {
		if (id == null) {
			return;
		}
		if (out.length() > 0) {
			out.append('|');
		}
		out.append(prefix).append(id);
	}

	private static Throwable rootCause(Throwable t) {
		Throwable current = t;
		// guard against a self-referencing or cyclic cause chain
		for (int depth = 0; depth < 20; depth++) {
			Throwable cause = current.getCause();
			if (cause == null || cause == current) {
				break;
			}
			current = cause;
		}
		return current;
	}
}
