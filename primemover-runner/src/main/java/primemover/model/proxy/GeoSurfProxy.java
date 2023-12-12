package primemover.model.proxy;

public class GeoSurfProxy extends HttpProxy implements ISessionProxy {
	private String location;

	public GeoSurfProxy(String url, Integer port, String username, String password, String location) {
		super(url, port, username, password);
		this.location = location;
	}

	public GeoSurfProxy() {
		super();
	}

	public String getLocation() {
		return location;
	}

	public void setLocation(String location) {
		this.location = location;
	}

}
