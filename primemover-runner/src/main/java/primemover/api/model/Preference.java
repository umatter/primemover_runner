package primemover.api.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A crawler configuration preference as delivered by the API
 * ({@code configuration.preferences[]}): a free-form name/value pair. The
 * decision function reads most of them; the runner itself only consumes
 * {@code cookie_pref} (see {@link primemover.util.consent.ConsentPolicy}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Preference {
	private Long id;
	private String name;
	private String description;
	private String value;
	private Boolean active;

	public Preference() {
	}

	public Preference(String name, String value) {
		this.name = name;
		this.value = value;
		this.active = Boolean.TRUE;
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

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}

	public Boolean getActive() {
		return active;
	}

	public void setActive(Boolean active) {
		this.active = active;
	}
}
