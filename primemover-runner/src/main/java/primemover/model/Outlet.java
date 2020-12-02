package primemover.model;

import java.util.Date;

public class Outlet {
	private long id;
	private String title;
	private String outlet;
	private double yhat_n;
	private String domain;
	private String url;
	private String redirectUrl;
	private double pi;
	private Date updatedAt;
	private Date createdAt;
	private double avgReachRank;
	private double avgReachPermillion;

	public Outlet() {
	}

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getOutlet() {
		return outlet;
	}

	public void setOutlet(String outlet) {
		this.outlet = outlet;
	}

	public double getYhat_n() {
		return yhat_n;
	}

	public void setYhat_n(double yhat_n) {
		this.yhat_n = yhat_n;
	}

	public String getDomain() {
		return domain;
	}

	public void setDomain(String domain) {
		this.domain = domain;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getRedirectUrl() {
		return redirectUrl;
	}

	public void setRedirectUrl(String redirectUrl) {
		this.redirectUrl = redirectUrl;
	}

	public double getPi() {
		return pi;
	}

	public void setPi(double pi) {
		this.pi = pi;
	}

	public Date getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(Date updatedAt) {
		this.updatedAt = updatedAt;
	}

	public Date getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Date createdAt) {
		this.createdAt = createdAt;
	}

	public double getAvgReachRank() {
		return avgReachRank;
	}

	public void setAvgReachRank(double avgReachRank) {
		this.avgReachRank = avgReachRank;
	}

	public double getAvgReachPermillion() {
		return avgReachPermillion;
	}

	public void setAvgReachPermillion(double avgReachPermillion) {
		this.avgReachPermillion = avgReachPermillion;
	}
}
