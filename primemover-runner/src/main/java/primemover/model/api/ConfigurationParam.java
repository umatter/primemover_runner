package primemover.model.api;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ConfigurationParam {
	
	private String pi;
	private String psi;
	private String kappa;
	private String tau;
	private String beta;
	private String alpha;
	private List<String> search_terms;
	private List<String> media_outlet_urls;
	
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


	public List<String> getSearch_terms() {
		return search_terms;
	}


	public void setSearch_terms(List<String> search_terms) {
		this.search_terms = search_terms;
	}


	public List<String> getMedia_outlet_urls() {
		return media_outlet_urls;
	}


	public void setMedia_outlet_urls(List<String> media_outlet_urls) {
		this.media_outlet_urls = media_outlet_urls;
	}

	
}
