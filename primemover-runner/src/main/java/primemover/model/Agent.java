package primemover.model;

public class Agent {
	
	public enum AgentType {
		CHROME,
		FIREFOX
	}
	
	private Long id;
	private AgentType type;
	private String name;
	private String description;
	
	public AgentType getType() {
		return type;
	}
	
	public Long getId() {
		return id;
	}
	public void setId(Long id) {
		this.id = id;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getDescription() {
		return description;
	}
	public void setDescription(String description) {
		this.description = description;
	}

	public Agent(Long id, AgentType type, String name, String description) {
		super();
		this.id = id;
		this.type = type;
		this.name = name;
		this.description = description;
	}

	public Agent() {
		super();
	}
	
	
}
