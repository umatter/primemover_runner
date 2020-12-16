package primemover.model.api;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ConfigurationParam {
	private long id;
	private double pi;
	private double psi;
	private int kappa;
	private double tau;
	private double beta;
	private double alpha;
	
	@JsonDeserialize(using = SearchTermDeserializer.class)
	private List<SearchTerm> search_terms;
	@JsonDeserialize(using = MediaOutletsDeserializer.class)
	private List<MediaOutlet> media_outlet_urls;
	
	public ConfigurationParam() {
	}


	public double getPi() {
		return pi;
	}


	public void setPi(double pi) {
		this.pi = pi;
	}


	public double getPsi() {
		return psi;
	}


	public void setPsi(double psi) {
		this.psi = psi;
	}


	public int getKappa() {
		return kappa;
	}


	public void setKappa(int kappa) {
		this.kappa = kappa;
	}


	public double getTau() {
		return tau;
	}


	public void setTau(double tau) {
		this.tau = tau;
	}


	public double getBeta() {
		return beta;
	}


	public void setBeta(double beta) {
		this.beta = beta;
	}


	public double getAlpha() {
		return alpha;
	}


	public void setAlpha(double alpha) {
		this.alpha = alpha;
	}


	/*
	public SearchTerms getSearch_terms() {
		return search_terms;
	}


	public void setSearch_terms(SearchTerms search_terms) {
		this.search_terms = search_terms;
	}


	public MediaOutletUrls getMedia_outlet_urls() {
		return media_outlet_urls;
	}


	public void setMedia_outlet_urls(MediaOutletUrls media_outlet_urls) {
		this.media_outlet_urls = media_outlet_urls;
	}
*/

	public long getId() {
		return id;
	}


	public List<MediaOutlet> getMedia_outlet_urls() {
		return media_outlet_urls;
	}


	public void setMedia_outlet_urls(List<MediaOutlet> outlets) {
		this.media_outlet_urls = outlets;
	}


	public void setId(long id) {
		this.id = id;
	}

	
}
