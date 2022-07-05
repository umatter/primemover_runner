package primemover.api.model;

import java.util.List;

public class IpLocation {

	private Long geoname_id;
	private String capital;
	private String country_flag;
	private String country_flag_emoji;
	private String country_flag_emoji_unicode;
	private String calling_code;
	private Boolean is_eu;
	private List<IpLanguage> language;
	
	public IpLocation() {
	}

	public Long getGeoname_id() {
		return geoname_id;
	}

	public void setGeoname_id(Long geoname_id) {
		this.geoname_id = geoname_id;
	}

	public String getCapital() {
		return capital;
	}

	public void setCapital(String capital) {
		this.capital = capital;
	}

	public String getCountry_flag() {
		return country_flag;
	}

	public void setCountry_flag(String country_flag) {
		this.country_flag = country_flag;
	}

	public String getCountry_flag_emoji() {
		return country_flag_emoji;
	}

	public void setCountry_flag_emoji(String country_flag_emoji) {
		this.country_flag_emoji = country_flag_emoji;
	}

	public String getCountry_flag_emoji_unicode() {
		return country_flag_emoji_unicode;
	}

	public void setCountry_flag_emoji_unicode(String country_flag_emoji_unicode) {
		this.country_flag_emoji_unicode = country_flag_emoji_unicode;
	}

	public String getCalling_code() {
		return calling_code;
	}

	public void setCalling_code(String calling_code) {
		this.calling_code = calling_code;
	}

	public Boolean getIs_eu() {
		return is_eu;
	}

	public void setIs_eu(Boolean is_eu) {
		this.is_eu = is_eu;
	}

	public List<IpLanguage> getLanguage() {
		return language;
	}

	public void setLanguage(List<IpLanguage> language) {
		this.language = language;
	}

	
}
