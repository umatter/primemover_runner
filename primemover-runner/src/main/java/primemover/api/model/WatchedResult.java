package primemover.api.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * GET /crawlers/{id}/watched -- the video ids this bot has already chosen.
 *
 * The API wraps every collection in "data"; this is that wrapper and nothing
 * more. Unknown properties are ignored so the endpoint can grow a meta block
 * without breaking a runner that has not been rebuilt.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class WatchedResult {

	private List<String> data;

	public List<String> getData() {
		return data;
	}

	public void setData(List<String> data) {
		this.data = data;
	}
}
