package primemover.model;

import java.util.HashMap;
import java.util.Map;

public class SelectionConfiguration {
	
	private long id;
	private double pi;
	private double psi;
	private int kappa;
	private double tau;
	private double beta;
	private double alpha;
	private Map<String, MediaOutlet> outlets;

	public SelectionConfiguration() {
	}

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
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

	public void setAlpha(double d) {
		this.alpha = d;
	}

	public Map<String, MediaOutlet> getOutlets() {
		return outlets;
	}

	public void setOutlets(HashMap<String, MediaOutlet> outlets) {
		this.outlets = outlets;
	}

	
}
