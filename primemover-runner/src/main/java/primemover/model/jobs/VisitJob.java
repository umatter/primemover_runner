package primemover.model.jobs;

/**
 * This job will reset the failure behaviour by default
 * 
 * @author domin
 *
 */
public class VisitJob extends BaseJob {

	private String url;

	public void setUrl(String value) {
		this.url = value;
	}

	public String getUrl() {
		return url;
	}

	public VisitJob() {
		super();
	}

	public VisitJob(Long id, String name, String description, String task, String url) {
		super(id, name, description, task, FAILSAVE_DEFAULT, true);

		this.url = url;
	}

}
