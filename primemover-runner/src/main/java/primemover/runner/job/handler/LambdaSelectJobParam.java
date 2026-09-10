package primemover.runner.job.handler;

import com.fasterxml.jackson.annotation.JsonProperty;

import primemover.model.Crawler;
import primemover.model.jobs.LambdaSelectJob;

import java.util.List;

public class LambdaSelectJobParam {

	private LambdaSelectJob job;
	private Crawler crawler;
	private String pagesource;
	private primemover.api.model.Configuration rawConfiguration;

	/**
	 * Video ids this bot has already chosen, or null when we could not tell.
	 *
	 * Serialised as already_watched because that is what the decision function
	 * reads. NULL and an empty list are different answers there: null means the
	 * history is unknown and the filter reports not_implemented, filtering
	 * nothing, exactly as it did before this field existed; an empty list is
	 * the positive answer "watched nothing" and counts as the filter applying.
	 * So a failure upstream must leave this null rather than set it to [].
	 */
	@JsonProperty("already_watched")
	private List<String> alreadyWatched;

	public LambdaSelectJobParam(LambdaSelectJob job, Crawler crawler, String pagesource) {
		super();
		this.job = job;
		this.crawler = crawler;
		this.pagesource = pagesource;
	}

	public LambdaSelectJobParam(LambdaSelectJob job, Crawler crawler, String pagesource, primemover.api.model.Configuration rawConfiguration) {
		super();
		this.job = job;
		this.crawler = crawler;
		this.pagesource = pagesource;
		this.rawConfiguration = rawConfiguration;
	}

	public LambdaSelectJob getJob() {
		return job;
	}

	public void setJob(LambdaSelectJob job) {
		this.job = job;
	}

	public Crawler getCrawler() {
		return crawler;
	}

	public void setCrawler(Crawler crawler) {
		this.crawler = crawler;
	}

	public String getPagesource() {
		return pagesource;
	}

	public void setPagesource(String pagesource) {
		this.pagesource = pagesource;
	}

	@JsonProperty("already_watched")
	public List<String> getAlreadyWatched() {
		return alreadyWatched;
	}

	@JsonProperty("already_watched")
	public void setAlreadyWatched(List<String> alreadyWatched) {
		this.alreadyWatched = alreadyWatched;
	}

	public primemover.api.model.Configuration getRawConfiguration() {
		return rawConfiguration;
	}

	public void setRawConfiguration(primemover.api.model.Configuration rawConfiguration) {
		this.rawConfiguration = rawConfiguration;
	}

}
