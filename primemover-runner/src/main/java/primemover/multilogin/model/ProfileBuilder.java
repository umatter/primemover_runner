package primemover.multilogin.model;

import java.util.Arrays;

import javax.annotation.Nullable;

public class ProfileBuilder {
	private Profile profile;

	private boolean hasBrowser = false;
	private boolean hasOperatingSystem = false;

	/**
	 * Starts building a profile, will set the defaults.
	 * 
	 * @param name
	 * @param group
	 */
	ProfileBuilder(String name, @Nullable String group) {
		super();

		if (group == null) {
			group = "";
		}

		profile = new Profile();
		profile.name = name;
		profile.enableLock = false;
		profile.googleServices = false;
		profile.group = group;
		profile.notes = "";
		setBrowser(Browser.Stealthfox);
		setOperatingSystem(OperatingSystem.Windows);
	}

	/**
	 * sets the browser type
	 * 
	 * @param browser
	 * @return
	 */
	public ProfileBuilder setBrowser(Browser browser) {
		profile.setBrowser(browser);
		hasBrowser = true;
		return this;
	}

	/**
	 * sets the operating system
	 * 
	 * @param os
	 * @return
	 */
	public ProfileBuilder setOperatingSystem(OperatingSystem os) {
		profile.setOs(os);
		hasOperatingSystem = true;
		return this;
	}

	/**
	 * Validates all required parameters to be available
	 * 
	 * @return true -> success, false -> something missing
	 */
	private boolean validateProfile() {
		return hasBrowser && hasOperatingSystem;
	}

	/**
	 * Adds a new extension to the browser
	 * 
	 * @param name
	 * @return
	 */
	public ProfileBuilder addExtension(String name) {
		Extensions extensions = profile.getExtensions();

		if (extensions == null) {
			extensions = new Extensions(true, Arrays.asList(name));
		} else {
			extensions.getNames().add(name);
		}

		return this;
	}

	/**
	 * Creates a profile instance
	 * 
	 * @throws IllegalArgumentException if the profile is not completely filled
	 * 
	 * @return an instance of {@link Profile}
	 */
	public Profile build() {
		if (!validateProfile()) {
			throw new IllegalArgumentException(
					"A profile must have at least a browser and an operating-system defined");
		}

		return profile;
	}
}
