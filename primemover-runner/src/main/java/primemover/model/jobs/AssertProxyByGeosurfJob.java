package primemover.model.jobs;

/*
 * Marker class for concrete implementation against geosurf
 */
public class AssertProxyByGeosurfJob extends AssertProxyJob {

	public AssertProxyByGeosurfJob() {
		super();
	}

	public AssertProxyByGeosurfJob(Long id, String name, String description, String ip, String country, String city) {
		super(id, name, description, ip, country, city);
	}
}
