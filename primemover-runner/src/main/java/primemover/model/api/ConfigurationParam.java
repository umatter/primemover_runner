package primemover.model.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ConfigurationParam {
	private long id;
	private int pi;
	private int psi;
	private int kappa;
	private int tau;
	private double beta;
	private int alpha;
	@JsonDeserialize(using = SearchTermsDeserializer.class)
	private SearchTerms search_terms;
	@JsonDeserialize(using = MediaOutletUrlsDeserializer.class)
	private MediaOutletUrls media_outlet_urls;
	
	public ConfigurationParam() {
	}


	public int getPi() {
		return pi;
	}


	public void setPi(int pi) {
		this.pi = pi;
	}


	public int getPsi() {
		return psi;
	}


	public void setPsi(int psi) {
		this.psi = psi;
	}


	public int getKappa() {
		return kappa;
	}


	public void setKappa(int kappa) {
		this.kappa = kappa;
	}


	public int getTau() {
		return tau;
	}


	public void setTau(int tau) {
		this.tau = tau;
	}


	public double getBeta() {
		return beta;
	}


	public void setBeta(double beta) {
		this.beta = beta;
	}


	public int getAlpha() {
		return alpha;
	}


	public void setAlpha(int alpha) {
		this.alpha = alpha;
	}


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


	public long getId() {
		return id;
	}


	public void setId(long id) {
		this.id = id;
	}

	
}
