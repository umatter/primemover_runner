package primemover.api.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Date;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Term {

	long  id;
	String bigram;
	String bigram_stem;
	String search_term;
	String refterm;
	long n_days_hits;
	long s_states_hits;
	double mean_hits_time;
	double mean_hits_states;
	String subject;
	long f_pid;
	long f_plr;
	long f_npld;
	long f_nplr;
	double chisq_pl;
	double a_p;
	double a_p_se;
	double a_p_tval;
	double a_p_pval;
	double b_p;
	double b_p_se;
	double b_p_tval;
	double b_p_pval;
	double pi_p;
	Date updated_at;
	Date created_at;	
	
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

	public String getBigram_stem() {
		return bigram_stem;
	}

	public void setBigram_stem(String bigram_stem) {
		this.bigram_stem = bigram_stem;
	}

	public String getSearch_term() {
		return search_term;
	}

	public void setSearch_term(String search_term) {
		this.search_term = search_term;
	}

	public String getRefterm() {
		return refterm;
	}

	public void setRefterm(String refterm) {
		this.refterm = refterm;
	}

	public long getN_days_hits() {
		return n_days_hits;
	}

	public void setN_days_hits(long n_days_hits) {
		this.n_days_hits = n_days_hits;
	}

	public long getS_states_hits() {
		return s_states_hits;
	}

	public void setS_states_hits(long s_states_hits) {
		this.s_states_hits = s_states_hits;
	}

	public double getMean_hits_time() {
		return mean_hits_time;
	}

	public void setMean_hits_time(double mean_hits_time) {
		this.mean_hits_time = mean_hits_time;
	}

	public double getMean_hits_states() {
		return mean_hits_states;
	}

	public void setMean_hits_states(double mean_hits_states) {
		this.mean_hits_states = mean_hits_states;
	}

	public String getSubject() {
		return subject;
	}

	public void setSubject(String subject) {
		this.subject = subject;
	}

	public long getF_pid() {
		return f_pid;
	}

	public void setF_pid(long f_pid) {
		this.f_pid = f_pid;
	}

	public long getF_plr() {
		return f_plr;
	}

	public void setF_plr(long f_plr) {
		this.f_plr = f_plr;
	}

	public long getF_npld() {
		return f_npld;
	}

	public void setF_npld(long f_npld) {
		this.f_npld = f_npld;
	}

	public long getF_nplr() {
		return f_nplr;
	}

	public void setF_nplr(long f_nplr) {
		this.f_nplr = f_nplr;
	}

	public double getChisq_pl() {
		return chisq_pl;
	}

	public void setChisq_pl(double chisq_pl) {
		this.chisq_pl = chisq_pl;
	}

	public double getA_p() {
		return a_p;
	}

	public void setA_p(double a_p) {
		this.a_p = a_p;
	}

	public double getA_p_se() {
		return a_p_se;
	}

	public void setA_p_se(double a_p_se) {
		this.a_p_se = a_p_se;
	}

	public double getA_p_tval() {
		return a_p_tval;
	}

	public void setA_p_tval(double a_p_tval) {
		this.a_p_tval = a_p_tval;
	}

	public double getA_p_pval() {
		return a_p_pval;
	}

	public void setA_p_pval(double a_p_pval) {
		this.a_p_pval = a_p_pval;
	}

	public double getB_p() {
		return b_p;
	}

	public void setB_p(double b_p) {
		this.b_p = b_p;
	}

	public double getB_p_se() {
		return b_p_se;
	}

	public void setB_p_se(double b_p_se) {
		this.b_p_se = b_p_se;
	}

	public double getB_p_tval() {
		return b_p_tval;
	}

	public void setB_p_tval(double b_p_tval) {
		this.b_p_tval = b_p_tval;
	}

	public double getB_p_pval() {
		return b_p_pval;
	}

	public void setB_p_pval(double b_p_pval) {
		this.b_p_pval = b_p_pval;
	}

	public double getPi_p() {
		return pi_p;
	}

	public void setPi_p(double pi_p) {
		this.pi_p = pi_p;
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
