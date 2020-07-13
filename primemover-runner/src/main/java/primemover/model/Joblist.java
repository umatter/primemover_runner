package primemover.model;

import java.util.ArrayList;
import java.util.List;

public class Joblist {

	private List<IJob> jobs;
	
	public List<IJob> getJobList() {
		return jobs;
	}
	
	public void appendJob(IJob job) {
		if (jobs == null) {
			jobs = new ArrayList<IJob>();
		}
		jobs.add(job);
	}
	
}
