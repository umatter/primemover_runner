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

	public VisitJob(Long id, String reference, String title, String url) {
		super(id, reference, title);
		
		this.url = url;
	}
	
	
	

	
}
