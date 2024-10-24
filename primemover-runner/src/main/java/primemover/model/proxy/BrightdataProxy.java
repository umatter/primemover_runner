package primemover.model.proxy;

import javax.annotation.Nullable;

public class BrightdataProxy extends HttpProxy {

	private String location;
	@Nullable
	private BrightdataOsEnum os = null;

	public BrightdataProxy(String url, Integer port, String username, String password, String location,
			@Nullable BrightdataOsEnum os) {
		super(url, port, username, password);
		this.location = location;
		this.os = os;
	}

	public BrightdataProxy() {
		super();
	}

	public String getLocation() {
		return location;
	}

	public void setLocation(String location) {
		this.location = location;
	}

	public @Nullable BrightdataOsEnum getOperatingSystem() {
		return os;
	}

	public void setOperatingSystem(@Nullable BrightdataOsEnum os) {
		this.os = os;
	}

	@Override
	public String getUsername() {
		// curl --proxy brd.superproxy.io:22225 --proxy-user
		// brd-customer-<USERNAME>-zone-<ZONE>-os-windows:<PASSWORD> "<TARGET SITE>"
		StringBuilder builder = new StringBuilder() //
				.append("brd-customer-").append(super.getUsername())// username
				.append("-").append(this.getLocation());// location
		if (os != null) {
			builder.append("-os-").append(os.getOperatingSystem());
		}
		return builder.toString();
	}
}
