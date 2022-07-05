package primemover.api.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;


@JsonIgnoreProperties(ignoreUnknown = true)
public class OutletResult {

	private List<Outlet> data;
	private String version;
	private String author;	
	
	public OutletResult() {
	}

	public List<Outlet> getData() {
		return data;
	}

	public void setData(List<Outlet> data) {
		this.data = data;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public String getAuthor() {
		return author;
	}

	public void setAuthor(String author) {
		this.author = author;
	}
	
	

}
