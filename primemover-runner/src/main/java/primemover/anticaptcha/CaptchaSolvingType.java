package primemover.anticaptcha;

public enum CaptchaSolvingType {
	NEVER, //
	ALWAYS, // before and after
	RANDOM, //
	BEFORE, //
	AFTER;

	/**
	 * Helper to handle the ALWAYS case against BEFOE and AFTER
	 * 
	 * @param that
	 * @return
	 */
	boolean includes(CaptchaSolvingType that) {
		if (this == ALWAYS) {
			return that == ALWAYS || that == BEFORE || that == AFTER;
		}

		if (that == ALWAYS) {
			return this == ALWAYS || this == BEFORE || this == AFTER;
		}

		return this == that;
	}
}