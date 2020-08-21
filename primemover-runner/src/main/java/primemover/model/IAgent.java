package primemover.model;

public interface IAgent {

	AgentType getType();

	Long getId();

	void setId(Long id);

	String getName();

	void setName(String name);

	String getDescription();

	void setDescription(String description);

}