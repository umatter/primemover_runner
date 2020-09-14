package primemover.model.api;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Configuration {
	
	private String name;
	private String description;
	private List<ConfigurationParam> params;

	public Configuration() {
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

	public List<ConfigurationParam> getParams() {
		return params;
	}

	public void setParams(List<ConfigurationParam> params) {
		this.params = params;
	}

	
}
