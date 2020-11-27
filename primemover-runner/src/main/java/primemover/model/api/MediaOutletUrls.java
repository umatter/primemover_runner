package primemover.model.api;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnySetter;

public class MediaOutletUrls {
	
	Map<String, String> outlets = new HashMap<>();

	public MediaOutletUrls() {
	}

	@JsonAnySetter
    void setOutlet(String key, String value) {
		outlets.put(key, value);
    }
}
