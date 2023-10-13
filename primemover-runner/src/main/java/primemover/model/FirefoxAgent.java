package primemover.model;

public class FirefoxAgent extends BasicAgent {

	public FirefoxAgent(Long id, String name, String description, String location) {
		super(id, AgentType.FIREFOX, name, description, location);
	}

	public static FirefoxAgent remapOf(IAgent old) {
		return new FirefoxAgent(old.getId(), old.getName(), old.getDescription(), old.getLocation());
	}
}
