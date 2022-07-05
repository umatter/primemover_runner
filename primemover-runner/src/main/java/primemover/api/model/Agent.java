package primemover.api.model;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Agent {
	private Long id;
	private String name;
	private String description;
	private String identification;
	private String multilogin_id;
	private String multilogin_profile;
	private Boolean active;
	private Date updated_at;
	private Date created_at;
	private String location;

	public Agent() {
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

	public String getMultilogin_id() {
		return multilogin_id;
	}

	public void setMultilogin_id(String multilogin_id) {
		this.multilogin_id = multilogin_id;
	}

	public String getMultilogin_profile() {
		return multilogin_profile;
	}

	public void setMultilogin_profile(String value) {
		this.multilogin_profile = value;
	}

	public Boolean getActive() {
		return active;
	}

	public void setActive(Boolean active) {
		this.active = active;
	}

	public Date getUpdated_at() {
		return updated_at;
	}

	public void setUpdated_at(Date updated_at) {
		this.updated_at = updated_at;
	}

	public Date getCreated_at() {
		return created_at;
	}

	public void setCreated_at(Date created_at) {
		this.created_at = created_at;
	}

	public String getIdentification() {
		return identification;
	}

	public void setIdentification(String identification) {
		this.identification = identification;
	}

	public String getLocation() {
		return location;
	}

	public void setLocation(String location) {
		this.location = location;
	}
}
