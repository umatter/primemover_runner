package primemover.model;

import org.springframework.util.StringUtils;

/*
 * Base class for Asserting the proxy
 */
public abstract class AssertProxy extends BaseJob {
	private String ip;
	private String country;
	private String city;
			
	public AssertProxy() {
		super();
	}	
	
	/**
	 * Constructor pass null to ip, country or city to skip the test. Be sure to pass at least one verfication value
	 * 
	 * @param id
	 * @param reference
	 * @param title
	 * @param ip
	 * @param country
	 * @param city
	 */
	public AssertProxy(Long id, String name, String description, String ip, String country, String city) {
		super(id, name, description);
		
		if (!StringUtils.hasText(ip) && !StringUtils.hasText(country) && !StringUtils.hasText(city)) {
			throw new IllegalArgumentException("pass at least one value not null and empty in ip, country or city");
		}
		
		this.ip = ip;
		this.country = country;
		this.city = city;
	}

	public String getIp() {
		return ip;
	}
	public void setIp(String ip) {
		this.ip = ip;
	}
	public String getCountry() {
		return country;
	}
	public void setCountry(String country) {
		this.country = country;
	}
	public String getCity() {
		return city;
	}
	public void setCity(String city) {
		this.city = city;
	}
	
	
}
