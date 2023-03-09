package primemover.multilogin.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import primemover.multilogin.json.deserializer.BrowserDeserializer;
import primemover.multilogin.json.deserializer.OperatingSystemDeserializer;

/**
 * Implementation of the profile type of multilogin. Currently only partially
 * implemented as a complete implementation is not required.
 * 
 * @author dburkolter
 *
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Profile {
	String name;
	@JsonDeserialize(using = OperatingSystemDeserializer.class)
	OperatingSystem os;
	@JsonDeserialize(using = BrowserDeserializer.class)
	Browser browser;
	String group;
	String notes;
	boolean googleServices;
	String startUrl;
	boolean enableLock;

	Extensions extensions;

	public Profile() {
		super();

		extensions = new Extensions();
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public OperatingSystem getOs() {
		return os;
	}

	public void setOs(OperatingSystem os) {
		this.os = os;
	}

	public Browser getBrowser() {
		return browser;
	}

	public void setBrowser(Browser browser) {
		this.browser = browser;
	}

	public String getGroup() {
		return group;
	}

	public void setGroup(String group) {
		this.group = group;
	}

	public String getNotes() {
		return notes;
	}

	public void setNotes(String notes) {
		this.notes = notes;
	}

	public boolean isGoogleServices() {
		return googleServices;
	}

	public void setGoogleServices(boolean googleServices) {
		this.googleServices = googleServices;
	}

	public String getStartUrl() {
		return startUrl;
	}

	public void setStartUrl(String startUrl) {
		this.startUrl = startUrl;
	}

	public boolean isEnableLock() {
		return enableLock;
	}

	public void setEnableLock(boolean enableLock) {
		this.enableLock = enableLock;
	}

	public Extensions getExtensions() {
		return extensions;
	}

	public void setExtensions(Extensions extensions) {
		this.extensions = extensions;
	}
}
