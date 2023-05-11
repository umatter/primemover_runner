package primemover.model;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public interface IAgent {

	@Nonnull
	AgentType getType();

	@Nonnull
	Long getId();

	void setId(Long id);

	@Nonnull
	String getName();

	void setName(String name);

	@Nullable
	String getDescription();

	void setDescription(String description);

	void setLocation(String newLocation);

	@Nonnull
	String getLocation();

}