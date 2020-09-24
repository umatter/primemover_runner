package primemover.model.jobs;

/*
 * Marker class for concrete implementation against geosurf
 */
public class AssertProxyByPrimemoverApiJob extends AssertProxyJob {

	public AssertProxyByPrimemoverApiJob() {
		super();
	}

	public AssertProxyByPrimemoverApiJob(Long id, String name, String description, String ip, String country, String city) {
		super(id, name, description, ip, country, city);
	}
}
