package primemover.model.proxy;

public class OxylabsProxy extends HttpProxy implements ISessionProxy {

	private static String oxylabsSessionMinutes = "30";

	private String location;

	public OxylabsProxy() {
		super();
		this.setUseHttps(true);
	}

	public OxylabsProxy(String url, Integer port, String username, String password, String location) {
		super(url, port, username, password);
		this.setUseHttps(true);
		this.setLocation(location);
	}

	public String getLocation() {
		return location;
	}

	public void setLocation(String location) {
		this.location = location;
	}

	@Override
	public String getUsername() {
		return new StringBuilder() //
				.append("customer-").append(super.getUsername())// username
				.append("-sessid-").append(Utils.SessionPlaceholder)// session
				.append("-").append(this.getLocation())// location
				.append("-sesstime-").append(oxylabsSessionMinutes) // session time
				.toString();
	}

}
