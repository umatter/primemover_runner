package primemover.runner.job.handler;

import primemover.model.Crawler;
import primemover.model.jobs.LambdaSelectJob;

public class LambdaSelectJobParam {

	private LambdaSelectJob job;
	private Crawler crawler;
	private String pagesource;

	public LambdaSelectJobParam(LambdaSelectJob job, Crawler crawler, String pagesource) {
		super();
		this.job = job;
		this.crawler = crawler;
		this.pagesource = pagesource;
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

}
