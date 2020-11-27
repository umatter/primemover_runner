package primemover.model.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ConfigurationParam {
	
	private String pi;
	private String psi;
	private String kappa;
	private String tau;
	private String beta;
	private String alpha;
	@JsonDeserialize(using = SearchTermsDeserializer.class)
	private SearchTerms search_terms;
	@JsonDeserialize(using = MediaOutletUrlsDeserializer.class)
	private MediaOutletUrls media_outlet_urls;
	
	public ConfigurationParam() {
	}


	public String getPi() {
		return pi;
	}


	public void setPi(String pi) {
		this.pi = pi;
	}


	public String getPsi() {
		return psi;
	}


	public void setPsi(String psi) {
		this.psi = psi;
	}


	public String getKappa() {
		return kappa;
	}


	public void setKappa(String kappa) {
		this.kappa = kappa;
	}


	public String getTau() {
		return tau;
	}


	public void setTau(String tau) {
		this.tau = tau;
	}


	public String getBeta() {
		return beta;
	}


	public void setBeta(String beta) {
		this.beta = beta;
	}


	public String getAlpha() {
		return alpha;
	}


	public void setAlpha(String alpha) {
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

	
}
