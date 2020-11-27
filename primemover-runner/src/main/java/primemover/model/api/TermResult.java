package primemover.model.api;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;


@JsonIgnoreProperties(ignoreUnknown = true)
public class TermResult {

	private List<Term> data;
	private String version;
	private String author;	
	
	public TermResult() {
	}

	public List<Term> getData() {
		return data;
	}

	public void setData(List<Term> data) {
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
