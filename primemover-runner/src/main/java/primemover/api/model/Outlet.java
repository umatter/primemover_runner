package primemover.api.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Date;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Outlet {

	private long id;
	private String title;
	private String outlet;
	private double yhat_n;
	private String congress;
	private String domain;
	private String url;
	private String redirect_url;
	private String media_type;
	private String pub_state;
	private double avg_reach_rank;
	private double avg_reach_permillion;
	private String awis_categories;
	private String source;
	private double pi;
	private Date updated_at;
	private Date created_at;
	
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

	public String getCongress() {
		return congress;
	}

	public void setCongress(String congress) {
		this.congress = congress;
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

	public String getRedirect_url() {
		return redirect_url;
	}

	public void setRedirect_url(String redirect_url) {
		this.redirect_url = redirect_url;
	}

	public String getMedia_type() {
		return media_type;
	}

	public void setMedia_type(String media_type) {
		this.media_type = media_type;
	}

	public String getPub_state() {
		return pub_state;
	}

	public void setPub_state(String pub_state) {
		this.pub_state = pub_state;
	}

	public double getAvg_reach_rank() {
		return avg_reach_rank;
	}

	public void setAvg_reach_rank(double avg_reach_rank) {
		this.avg_reach_rank = avg_reach_rank;
	}

	public double getAvg_reach_permillion() {
		return avg_reach_permillion;
	}

	public void setAvg_reach_permillion(double avg_reach_permillion) {
		this.avg_reach_permillion = avg_reach_permillion;
	}

	public String getAwis_categories() {
		return awis_categories;
	}

	public void setAwis_categories(String awis_categories) {
		this.awis_categories = awis_categories;
	}

	public String getSource() {
		return source;
	}

	public void setSource(String source) {
		this.source = source;
	}

	public double getPi() {
		return pi;
	}

	public void setPi(double pi) {
		this.pi = pi;
	}

	public Date getUpdated_at() {
		return updated_at;
	}

	public void setUpdated_at(Date updated_at) {
		this.updated_at = updated_at;
	}

	public Date getCreated_at() {
		return created_at;
	}

	public void setCreated_at(Date created_at) {
		this.created_at = created_at;
	}
	
	

}
