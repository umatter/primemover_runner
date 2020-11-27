package primemover.model.api;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SearchTerms {

	private List<String> bigrams;
	private List<String> instagram;
	
	public SearchTerms() {
	}

	public List<String> getBigrams() {
		return bigrams;
	}

	public void setBigrams(List<String> bigrams) {
		this.bigrams = bigrams;
	}

	public List<String> getInstagram() {
		return instagram;
	}

	public void setInstagram(List<String> instagram) {
		this.instagram = instagram;
	}

	
}
