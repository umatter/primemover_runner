package primemover.model.proxy;

public final class Utils {

	public static String SessionPlaceholder = "%%SESSIONID%%";
	public static String PasswordPlaceholder = "%%PASSWORD%%";
	/**
	 * Stands in for the Oxylabs session token NAME (sessid or sessid_oneip). The
	 * choice is a runtime setting, so the proxy model emits the placeholder and
	 * {@link primemover.capture.SessionDecorator} resolves it.
	 */
	public static String SessionTokenPlaceholder = "%%SESSIONTOKEN%%";
}
