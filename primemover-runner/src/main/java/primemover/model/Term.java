package primemover.model;

public class Term {
	long  id;
	String bigram;
	String bigramStem;
	String searchTerm;
	String refTerm;

	public Term() {
	}

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public String getBigram() {
		return bigram;
	}

	public void setBigram(String bigram) {
		this.bigram = bigram;
	}

	public String getBigramStem() {
		return bigramStem;
	}

	public void setBigramStem(String bigramStem) {
		this.bigramStem = bigramStem;
	}

	public String getSearchTerm() {
		return searchTerm;
	}

	public void setSearchTerm(String searchTerm) {
		this.searchTerm = searchTerm;
	}

	public String getRefTerm() {
		return refTerm;
	}

	public void setRefTerm(String refTerm) {
		this.refTerm = refTerm;
	}
}
