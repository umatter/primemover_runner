package primemover.model;

import java.util.Map;

public class SelectionConfiguration {
	
	private long id;
	private int pi;
	private int psi;
	private int kappa;
	private int tau;
	private double beta;
	private int alpha;
	private Map<String, String> outlets;

	public SelectionConfiguration() {
	}

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
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

	public Map<String, String> getOutlets() {
		return outlets;
	}

	public void setOutlets(Map<String, String> outlets) {
		this.outlets = outlets;
	}

	
}
