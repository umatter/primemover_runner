package primemover.model;

public class BasicAgent implements IAgent {
	
	private Long id;
	private AgentType type;
	private String name;
	private String description;
	
	/* (non-Javadoc)
	 * @see primemover.model.IAgent#getType()
	 */
	@Override
	public AgentType getType() {
		return type;
	}
	
	/* (non-Javadoc)
	 * @see primemover.model.IAgent#getId()
	 */
	@Override
	public Long getId() {
		return id;
	}
	/* (non-Javadoc)
	 * @see primemover.model.IAgent#setId(java.lang.Long)
	 */
	@Override
	public void setId(Long id) {
		this.id = id;
	}
	/* (non-Javadoc)
	 * @see primemover.model.IAgent#getName()
	 */
	@Override
	public String getName() {
		return name;
	}
	/* (non-Javadoc)
	 * @see primemover.model.IAgent#setName(java.lang.String)
	 */
	@Override
	public void setName(String name) {
		this.name = name;
	}
	/* (non-Javadoc)
	 * @see primemover.model.IAgent#getDescription()
	 */
	@Override
	public String getDescription() {
		return description;
	}
	/* (non-Javadoc)
	 * @see primemover.model.IAgent#setDescription(java.lang.String)
	 */
	@Override
	public void setDescription(String description) {
		this.description = description;
	}

	public BasicAgent(Long id, AgentType type, String name, String description) {
		super();
		this.id = id;
		this.type = type;
		this.name = name;
		this.description = description;
	}

	public BasicAgent() {
		super();
	}
	
	
}
