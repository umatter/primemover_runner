package primemover.util.consent;

/**
 * Outcome of one {@link ConsentHandler#handle} call.
 *
 * @param present whether a consent dialog was detected on the page
 * @param clicked whether a button was clicked
 * @param cmp     which consent-management platform was recognised (e.g. {@code youtube}, {@code onetrust}, {@code generic}), null if none
 * @param label   visible text or aria-label of the button clicked, null if none
 * @param action  the choice that was applied ({@code accept}/{@code reject}), or {@code ignore}/{@code none}
 * @param stillPresent whether the dialog was still there after the click (verification failed)
 */
public record ConsentResult(boolean present, boolean clicked, String cmp, String label, String action,
		boolean stillPresent) {

	public static final ConsentResult NOT_PRESENT = new ConsentResult(false, false, null, null, "none", false);

	public static ConsentResult ignored(String cmp) {
		return new ConsentResult(true, false, cmp, null, "ignore", true);
	}
}
