package primemover.model;

public class VisitJob extends BaseJob  {
	
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

	public VisitJob(Long id, String name, String description, String url) {
		super(id, name, description);
		
		this.url = url;
	}
	
	
	

	
}
