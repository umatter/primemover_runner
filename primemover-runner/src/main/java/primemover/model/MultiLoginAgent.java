package primemover.model;

public class MultiLoginAgent extends BasicAgent {
	
	private String multiLoginId;

	public MultiLoginAgent(Long id, String name, String description, String multiLoginId, String location) {
		super(id, AgentType.MULTILOGIN, name, description, location);
		
		this.multiLoginId = multiLoginId;
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
}
