package primemover.model;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public class Joblist {
	private Long id;
	private Boolean active;
	private Boolean processing;
	private Boolean processed;
	private Boolean reviewed;
	private ZonedDateTime startTime;
	private List<IJob> jobs;

	public List<IJob> getJobs() {
		return jobs;
	}

	public void setJobs(List<IJob> jobs) {
		this.jobs = jobs;
	}

	public void appendJob(IJob job) {
		if (jobs == null) {
			jobs = new ArrayList<IJob>();
		}
		jobs.add(job);
	}

	public ZonedDateTime getStartTime() {
		return startTime;
	}

	public void setStartTime(ZonedDateTime startTime) {
		this.startTime = startTime;
	}

	public Boolean isActive() {
		return active;
	}

	public void setActive(Boolean active) {
		this.active = active;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Boolean isProcessed() {
		return processed;
	}

	public void setProcessed(Boolean processed) {
		this.processed = processed;
	}

	public Boolean isReviewed() {
		return reviewed;
	}

	public void setReviewed(Boolean reviewed) {
		this.reviewed = reviewed;
	}

	public Boolean isProcessing() {
		return processing;
	}

	public void setProcessing(Boolean processing) {
		this.processing = processing;
	}

	public boolean hasJobs() {
		return !jobs.isEmpty();
	}
}
