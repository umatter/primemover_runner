package primemover.model;

import java.util.HashMap;

public class Configuration {
	private int id;
	private String name;
	private String description;
	private HashMap<String, String> properties;
	
	public int getId() {
		return id;
	}
	public void setId(int id) {
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
	
	public HashMap<String, String> getProperties() {
		return properties;
	}
	
	public void setProperties(HashMap<String, String> properties) {
		this.properties = properties;
	}
	
	
	
}
