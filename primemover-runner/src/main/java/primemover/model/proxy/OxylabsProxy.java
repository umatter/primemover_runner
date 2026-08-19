package primemover.model.proxy;

/**
 * Oxylabs residential proxy.
 *
 * <p>
 * All targeting and session control is carried in the HTTP Basic username. The
 * documented grammar is
 *
 * <pre>
 * customer-{USER}-{LOCATION}-{sessid|sessid_oneip}-{SESSION}-sesstime-{MINUTES}
 * </pre>
 *
 * with the location tokens ahead of the session token. Every published Oxylabs
 * example follows that order and none places a location token after the session
 * token; whether the order is actually enforced is not documented, and a
 * mis-ordered username can fail open (a working proxy on an arbitrary exit IP),
 * so the documented order is emitted unconditionally.
 *
 * <p>
 * The session token name is left as a placeholder and resolved by
 * {@link primemover.capture.SessionDecorator}, which knows the runtime
 * configuration -- see {@code oxylabs.session.oneip}. The account name is
 * handled the same way when the proxy record does not carry one, so the Oxylabs
 * credentials can live once on the runner host instead of being copied into
 * every proxy row and every experiment spec.
 */
public class OxylabsProxy extends HttpProxy implements ISessionProxy {

	private static String oxylabsSessionMinutes = "30";

	/** The value as configured on the agent, kept for logging and debugging. */
	private String rawLocation;

	/** {@link #rawLocation} translated into Oxylabs tokens; may be empty. */
	private String location = "";

	public OxylabsProxy() {
		super();
		this.setUseHttps(true);
	}

	public OxylabsProxy(String url, Integer port, String username, String password, String location) {
		super(url, port, username, password);
		this.setUseHttps(true);
		this.setLocation(location);
	}

	/**
	 * @return the Oxylabs token form of the location, e.g.
	 *         {@code cc-US-city-chicago}, or an empty string when untargeted
	 */
	public String getLocation() {
		return location;
	}

	/**
	 * @return the location exactly as configured on the agent, before translation
	 */
	public String getRawLocation() {
		return rawLocation;
	}

	/**
	 * Translates eagerly so that an unmappable location fails while the crawler is
	 * being converted, rather than deep inside request handling once a run is
	 * already under way.
	 *
	 * @throws IllegalArgumentException if the value cannot be mapped
	 */
	public void setLocation(String location) {
		this.rawLocation = location;
		this.location = OxylabsLocation.toTokens(location);
	}

	@Override
	public String getUsername() {
		String account = super.getUsername();
		if (account == null || account.trim().isEmpty()) {
			// Filled in from the runner's own configuration by SessionDecorator, so the
			// account need not be repeated in every proxy row.
			account = Utils.CustomerPlaceholder;
		}

		StringBuilder builder = new StringBuilder() //
				.append("customer-").append(account); // username

		if (location != null && !location.isEmpty()) {
			builder.append("-").append(location); // location, ahead of the session token
		}

		return builder //
				.append("-").append(Utils.SessionTokenPlaceholder) // sessid or sessid_oneip
				.append("-").append(Utils.SessionPlaceholder) // session
				.append("-sesstime-").append(oxylabsSessionMinutes) // session time
				.toString();
	}

}
