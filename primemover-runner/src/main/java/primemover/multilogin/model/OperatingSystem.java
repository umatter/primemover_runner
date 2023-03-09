package primemover.multilogin.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Available OS of MultiLogin
 * 
 * @author dburkolter
 *
 */
public enum OperatingSystem {
	Windows("win"), Mac("mac"), Linux("lin"), Android("android");

	private static final Map<String, OperatingSystem> BY_DISPLAYNAME = new HashMap<>();

	static {
		for (OperatingSystem e : values()) {
			BY_DISPLAYNAME.put(e.displayName, e);
		}
	}

	private final String displayName;

	private OperatingSystem(String displayName) {
		this.displayName = displayName;
	}

	public String getDisplayName() {
		return displayName;
	}

	public static OperatingSystem valueOfDisplayName(String displayName) {
		OperatingSystem res = BY_DISPLAYNAME.get(displayName);

		if (res == null) {
			throw new IllegalArgumentException("Value '" + displayName + "' was not found in enum");
		}

		return res;
	}

}
