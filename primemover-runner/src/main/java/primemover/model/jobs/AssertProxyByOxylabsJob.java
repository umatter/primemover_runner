package primemover.model.jobs;

/**
 * Assert the exit IP's geolocation via Oxylabs' own check endpoint.
 *
 * <p>
 * Replaces the GeoSurf-era assertion. Third-party IP checkers are a restricted
 * target on Oxylabs, which mandates {@code ip.oxylabs.io} instead, so this is
 * the only supported way to verify that a crawler actually came out where its
 * experimental condition claims.
 */
public class AssertProxyByOxylabsJob extends AssertProxyJob {

	public AssertProxyByOxylabsJob() {
		super();
	}

	public AssertProxyByOxylabsJob(Long id, String name, String description, String ip, String country, String city) {
		super(id, name, description, ip, country, city);
	}
}
