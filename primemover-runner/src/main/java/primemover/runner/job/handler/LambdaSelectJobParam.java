package primemover.runner.job.handler;

import primemover.model.Crawler;
import primemover.model.jobs.LambdaSelectJob;

public class LambdaSelectJobParam {

	private LambdaSelectJob job;
	private Crawler crawler;
	private String pagesource;
	private primemover.api.model.Configuration rawConfiguration;

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

	public primemover.api.model.Configuration getRawConfiguration() {
		return rawConfiguration;
	}

	public void setRawConfiguration(primemover.api.model.Configuration rawConfiguration) {
		this.rawConfiguration = rawConfiguration;
	}

}
