package primemover.model;

/*
 * Marker class for concrete implementation against geosurf
 */
public class AssertProxyByGeosurf extends AssertProxy {

	public AssertProxyByGeosurf() {
		super();
	}

	public AssertProxyByGeosurf(Long id, String name, String description, String ip, String country, String city) {
		super(id, name, description, ip, country, city);
	}
}
