package primemover.model;


public class MultiLoginAgent extends BasicAgent {
	
	private String multiLoginId;
	private String multiLoginCreationJson;

	public MultiLoginAgent(Long id, String name, String description, String location, String multiLoginId, String multiloginCreationJson) {
		super(id, AgentType.MULTILOGIN, name, description, location);
		this.setMultiLoginId(multiLoginId);
		this.setMultiLoginCreationJson(multiloginCreationJson);
	}

	public MultiLoginAgent() {
		super(-1l, AgentType.MULTILOGIN, "", "", "");
	}

	public String getMultiLoginId() {
		return multiLoginId;
	}

	public void setMultiLoginId(String multiLoginId) {
		this.multiLoginId = multiLoginId;
	}
	

	public String getMultiLoginCreationJson() {
		return multiLoginCreationJson;
	}

	public void setMultiLoginCreationJson(String multiLoginCreationJson) {
		
		this.multiLoginCreationJson = multiLoginCreationJson;
	}

	
}
