package primemover.model.jobs;

import java.util.HashMap;
import java.util.Map;

public enum Attribute {
	TEXT("text"), HREF("href");

	private final String attributeName;

	private static final Map<String, Attribute> hashByAttribute = new HashMap<>();

	static {
		for (Attribute attr : Attribute.values()) {
			hashByAttribute.put(attr.getAttributeName(), attr);
		}
	}

	Attribute(String attributeName) {
		this.attributeName = attributeName;
	}

	public String getAttributeName() {
		return attributeName;
	}

	public static Attribute of(String attributeName) {
		Attribute result = hashByAttribute.get(attributeName);

		if (result == null) {
			throw new IllegalArgumentException("The attribute '" + attributeName + "' was not found!");
		}

		return result;
	}
}
