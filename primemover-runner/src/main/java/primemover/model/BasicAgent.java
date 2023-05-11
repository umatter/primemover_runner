package primemover.model;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class BasicAgent implements IAgent {

	@Nonnull
	private Long id;
	@Nonnull
	private AgentType type;
	@Nonnull
	private String name;
	@Nullable
	private String description;
	@Nonnull
	private String location;

	public BasicAgent(Long id, AgentType type, String name, String description, String location) {
		super();
		this.id = id;
		this.type = type;
		this.name = name;
		this.description = description;
		this.location = location;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see primemover.model.IAgent#getType()
	 */
	@Override
	@Nonnull
	public AgentType getType() {
		return type;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see primemover.model.IAgent#getId()
	 */
	@Override
	@Nonnull
	public Long getId() {
		return id;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see primemover.model.IAgent#setId(java.lang.Long)
	 */
	@Override
	public void setId(Long id) {
		this.id = id;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see primemover.model.IAgent#getName()
	 */
	@Override
	@Nonnull
	public String getName() {
		return name;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see primemover.model.IAgent#setName(java.lang.String)
	 */
	@Override
	public void setName(String name) {
		this.name = name;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see primemover.model.IAgent#getDescription()
	 */
	@Override
	@Nullable
	public String getDescription() {
		return description;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see primemover.model.IAgent#setDescription(java.lang.String)
	 */
	@Override
	public void setDescription(String description) {
		this.description = description;
	}

	@Override
	public void setLocation(String newLocation) {
		this.location = newLocation;
	}

	@Override
	@Nonnull
	public String getLocation() {
		return this.location;
	}

}
