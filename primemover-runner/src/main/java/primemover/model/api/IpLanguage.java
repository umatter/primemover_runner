package primemover.model.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public class IpLanguage {

	private String code;
	private String name;
	@JsonProperty("native")
	private String _native;
	
	public IpLanguage() {
	}

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String get_native() {
		return _native;
	}

	public void set_native(String _native) {
		this._native = _native;
	}

	
}
