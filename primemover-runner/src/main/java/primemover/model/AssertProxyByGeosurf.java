package primemover.model;

/*
 * Marker class for concrete implementation against geosurf
 */
public class AssertProxyByGeosurf extends AssertProxy {

	public AssertProxyByGeosurf() {
		super();
	}

	public AssertProxyByGeosurf(Long id, String reference, String title, String ip, String country, String city) {
		super(id, reference, title, ip, country, city);
	}
}
