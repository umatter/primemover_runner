package primemover.model;

public class MultiLoginAgent extends BasicAgent {
	
	private String multiLoginId;

	public MultiLoginAgent(Long id, String name, String description, String multiLoginId) {
		super(id, AgentType.MULTILOGIN, name, description);
		
		this.multiLoginId = multiLoginId;
	}

	public MultiLoginAgent() {
	}

	public String getMultiLoginId() {
		return multiLoginId;
	}

	public void setMultiLoginId(String multiLoginId) {
		this.multiLoginId = multiLoginId;
	}
}
