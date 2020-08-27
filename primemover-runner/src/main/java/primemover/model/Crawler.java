package primemover.model;

public class Crawler {

	private Long id;
	private String name;
	private String description;
	private IAgent agent;
	private Configuration config;
	private IProxy proxy;
	
	
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


	public Configuration getConfig() {
		return config;
	}


	public void setConfig(Configuration config) {
		this.config = config;
	}


	public IProxy getProxy() {
		return proxy;
	}


	public void setProxy(IProxy proxy) {
		this.proxy = proxy;
	}


	public void setAgent(IAgent agent) {
		this.agent = agent;
	}


	public IAgent getAgent() {
		return agent;
	}
}
