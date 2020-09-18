package primemover.model;

public class GeoSurfProxy extends HttpProxy {
	private String location;
	private String sessionId;

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

	public String getSessionId() {
		return sessionId;
	}

	public void setSessionId(String sessionId) {
		this.sessionId = sessionId;
	}

	
}
