package primemover.util;

import org.openqa.selenium.WebElement;

import com.fasterxml.jackson.annotation.JsonIgnore;

import primemover.model.MediaOutlet;

public class SelectionItem {
	private int rank;
	private boolean skiped = false;
	
	@JsonIgnore
	private WebElement element; // can't serialize this
	
	private String url;
	private String normalizedUrl;
	private boolean isKnown;
	private MediaOutlet outlet;
	private double d_tilde_i_j_t;
	private double u_raw;
	private double u_final;
	private double exp_u;
	private double probability;
	private double epsilon;
	
	private double alpha;
	private double beta;
	private double tau;
	
	private double prob_LowerEnd;
	private double prob_UpperEnd;
	
	private boolean selected = false;

	public SelectionItem() {
	}

	public int getRank() {
		return rank;
	}

	public void setRank(int rank) {
		this.rank = rank;
	}

	public String getNormalizedUrl() {
		return normalizedUrl;
	}

	public void setNormalizedUrl(String normalizedUrl) {
		this.normalizedUrl = normalizedUrl;
	}

	public boolean isSkiped() {
		return skiped;
	}

	public void setSkiped(boolean skiped) {
		this.skiped = skiped;
	}

	public String getUrl() {
		return this.url;
	}
	public void setUrl(String urlString) {
		this.url =  urlString;
	}

	public WebElement getElement() {
		return element;
	}

	public void setElement(WebElement element) {
		this.element = element;
	}

	public boolean isKnown() {
		return isKnown;
	}

	public void setKnown(boolean isKnown) {
		this.isKnown = isKnown;
	}

	public MediaOutlet getOutlet() {
		return outlet;
	}

	public void setOutlet(MediaOutlet outlet) {
		this.outlet = outlet;
	}

	public double getD_tilde_i_j_t() {
		return d_tilde_i_j_t;
	}

	public void setD_tilde_i_j_t(double d_tilde_i_j_t) {
		this.d_tilde_i_j_t = d_tilde_i_j_t;
	}

	public double getU_final() {
		return u_final;
	}

	public void setU_final(double u) {
		this.u_final = u;
	}

	public double getEpsilon() {
		return epsilon;
	}

	public void setEpsilon(double epsilon) {
		this.epsilon = epsilon;
	}

	public double getU_raw() {
		return u_raw;
	}

	public void setU_raw(double u) {
		this.u_raw = u;
	}

	public double getExp_u() {
		return exp_u;
	}

	public void setExp_u(double exp_u) {
		this.exp_u = exp_u;
	}

	public double getProbability() {
		return probability;
	}

	public void setProbability(double probability) {
		this.probability = probability;
	}

	public double getProb_LowerEnd() {
		return prob_LowerEnd;
	}

	public void setProb_LowerEnd(double prob_LowerEnd) {
		this.prob_LowerEnd = prob_LowerEnd;
	}

	public double getProb_UpperEnd() {
		return prob_UpperEnd;
	}

	public void setProb_UpperEnd(double prob_UpperEnd) {
		this.prob_UpperEnd = prob_UpperEnd;
	}

	public boolean isSelected() {
		return selected;
	}

	public void setSelected(boolean selected) {
		this.selected = selected;
	}

	public double getAlpha() {
		return alpha;
	}

	public void setAlpha(double alpha) {
		this.alpha = alpha;
	}

	public double getBeta() {
		return beta;
	}

	public void setBeta(double beta) {
		this.beta = beta;
	}

	public double getTau() {
		return tau;
	}

	public void setTau(double tau) {
		this.tau = tau;
	}
}
