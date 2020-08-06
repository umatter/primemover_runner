package primemover.model.geosurf;

public class IpInfo {
	private String ip;
	private String country;
	private String state;
	private String city;
	private Double[] loc;
	private String postal;
	private String isp;
	private String asn;
	private String organization;
	
	public IpInfo() {
		super();
	}
	
	public IpInfo(String ip, String country, String state, String city, Double[] loc, String postal, String isp, String asn,
			String organization) {
		super();
		this.ip = ip;
		this.country = country;
		this.state = state;
		this.city = city;
		this.loc = loc;
		this.postal = postal;
		this.isp = isp;
		this.asn = asn;
		this.organization = organization;
	}

	
	public String getCountry() {
		return country;
	}

	public void setCountry(String country) {
		this.country = country;
	}

	public String getIp() {
		return ip;
	}

	public void setIp(String ip) {
		this.ip = ip;
	}

	public String getState() {
		return state;
	}

	public void setState(String state) {
		this.state = state;
	}

	public String getCity() {
		return city;
	}

	public void setCity(String city) {
		this.city = city;
	}

	public Double[] getLoc() {
		return loc;
	}

	public void setLoc(Double[] loc) {
		this.loc = loc;
	}

	public String getPostal() {
		return postal;
	}

	public void setPostal(String postal) {
		this.postal = postal;
	}

	public String getIsp() {
		return isp;
	}

	public void setIsp(String isp) {
		this.isp = isp;
	}

	public String getAsn() {
		return asn;
	}

	public void setAsn(String asn) {
		this.asn = asn;
	}

	public String getOrganization() {
		return organization;
	}

	public void setOrganization(String organization) {
		this.organization = organization;
	}
	
	
	
	
}
