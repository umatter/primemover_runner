package primemover.multilogin.ml6.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Available browser of Multilogin
 * 
 * @author dburkolter
 *
 */
public enum Browser {
	Mimic("mimic"), Stealthfox("stealthfox"), Mimic_Mobile("mimic_mobile");

	private static final Map<String, Browser> BY_DISPLAYNAME = new HashMap<>();

	static {
		for (Browser e : values()) {
			BY_DISPLAYNAME.put(e.displayName, e);
		}
	}

	private final String displayName;

	private Browser(String displayName) {
		this.displayName = displayName;
	}

	/**
	 * The name that is displayed in the json of multilogin
	 * 
	 * @return
	 */
	public String getDisplayName() {
		return displayName;
	}

	/**
	 * Gets an instance derived from the display name
	 * 
	 * @param displayName
	 * @return
	 */
	public static Browser valueOfDisplayName(String displayName) {
		Browser res = BY_DISPLAYNAME.get(displayName);

		if (res == null) {
			throw new IllegalArgumentException("Value '" + displayName + "' was not found in enum");
		}

		return res;
	}

}
