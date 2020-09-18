package primemover.model.api;

import java.time.ZonedDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Queue {
	private Integer id;
	private String name;
	private String description;
	private Integer order;
	private Boolean active;
	private Boolean success;
	private Boolean failure;
	private ZonedDateTime start_at;
	private ZonedDateTime updated_at;
	private ZonedDateTime created_at;
	private List<Job> jobs;
	
	public Queue() {
		// nothing to do
	}

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
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

	public Integer getOrder() {
		return order;
	}

	public void setOrder(Integer order) {
		this.order = order;
	}

	public Boolean getActive() {
		return active;
	}

	public void setActive(Boolean active) {
		this.active = active;
	}

	public Boolean getSuccess() {
		return success;
	}

	public void setSuccess(Boolean success) {
		this.success = success;
	}

	public Boolean getFailure() {
		return failure;
	}

	public void setFailure(Boolean failure) {
		this.failure = failure;
	}
	
	public ZonedDateTime getStart_at() {
		return start_at;
	}
	
	public void setStart_at(ZonedDateTime start_at) {
		this.start_at = start_at;
	}

	public ZonedDateTime getUpdated_at() {
		return updated_at;
	}

	public void setUpdated_at(ZonedDateTime updated_at) {
		this.updated_at = updated_at;
	}

	public ZonedDateTime getCreated_at() {
		return created_at;
	}

	public void setCreated_at(ZonedDateTime created_at) {
		this.created_at = created_at;
	}

	public List<Job> getJobs() {
		return jobs;
	}

	public void setJobs(List<Job> jobs) {
		this.jobs = jobs;
	}

	
}
