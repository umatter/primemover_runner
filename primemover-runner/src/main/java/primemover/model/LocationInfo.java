package primemover.model;

public class LocationInfo {

	private String ip;
	private String type;
	private String countryCode;
	private String countryName;
	private String regionCode;
	private String regionName;
	private String city;
	private String zip;
	private Double latitude;
	private Double longitude;
	
	public LocationInfo() {
	}
	
	@Override
	public String toString() {
		return "LocationInfo [ip=" + ip + ", type=" + type + ", countryCode=" + countryCode + ", countryName="
				+ countryName + ", regionCode=" + regionCode + ", regionName=" + regionName + ", city=" + city
				+ ", zip=" + zip + ", latitude=" + latitude + ", longitude=" + longitude + "]";
	}

	public String getIp() {
		return ip;
	}

	public void setIp(String ip) {
		this.ip = ip;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getCountryCode() {
		return countryCode;
	}

	public void setCountryCode(String country_code) {
		this.countryCode = country_code;
	}

	public String getCountryName() {
		return countryName;
	}

	public void setCountryName(String country_name) {
		this.countryName = country_name;
	}

	public String getRegionCode() {
		return regionCode;
	}

	public void setRegionCode(String region_code) {
		this.regionCode = region_code;
	}

	public String getRegionName() {
		return regionName;
	}

	public void setRegionName(String region_name) {
		this.regionName = region_name;
	}

	public String getCity() {
		return city;
	}

	public void setCity(String city) {
		this.city = city;
	}

	public String getZip() {
		return zip;
	}

	public void setZip(String zip) {
		this.zip = zip;
	}

	public Double getLatitude() {
		return latitude;
	}

	public void setLatitude(Double latitude) {
		this.latitude = latitude;
	}

	public Double getLongitude() {
		return longitude;
	}

	public void setLongitude(Double longitude) {
		this.longitude = longitude;
	}

	
}
