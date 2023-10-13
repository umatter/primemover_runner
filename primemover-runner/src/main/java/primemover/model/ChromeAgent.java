package primemover.model;

public class ChromeAgent extends BasicAgent {

	public ChromeAgent(Long id, String name, String description, String location) {
		super(id, AgentType.CHROME, name, description, location);
	}

	public static ChromeAgent remapOf(IAgent old) {
		return new ChromeAgent(old.getId(), old.getName(), old.getDescription(), old.getLocation());
	}
}
