package com.sushil.elasticsearch.model.cpu;

//1️⃣ Current CPU Usage
public class CpuUsageRecord {
	private String hostname;
	private double systemPct;
	private double userPct;
	private double ioWaitPct;
	private double stealPct;
	private double nicePct;
	private double totalPct;
	private double idlePct;
	private String environment;
	private String ip_address;

	public CpuUsageRecord() {
	}

	// Getters and Setters
	public String gethostname() {
		return hostname;
	}

	public void sethostname(String hostname) {
		this.hostname = hostname;
	}

	public double getSystemPct() {
		return systemPct;
	}

	public void setSystemPct(double systemPct) {
		this.systemPct = systemPct;
	}

	public double getUserPct() {
		return userPct;
	}

	public void setUserPct(double userPct) {
		this.userPct = userPct;
	}

	public double getIoWaitPct() {
		return ioWaitPct;
	}

	public void setIoWaitPct(double ioWaitPct) {
		this.ioWaitPct = ioWaitPct;
	}

	public double getStealPct() {
		return stealPct;
	}

	public void setStealPct(double stealPct) {
		this.stealPct = stealPct;
	}

	public double getNicePct() {
		return nicePct;
	}

	public void setNicePct(double nicePct) {
		this.nicePct = nicePct;
	}

	public double getTotalPct() {
		return totalPct;
	}

	public void setTotalPct(double totalPct) {
		this.totalPct = totalPct;
	}

	public double getIdlePct() {
		return idlePct;
	}

	public void setIdlePct(double idlePct) {
		this.idlePct = idlePct;
	}

	public String getEnvironment() {
		return environment;
	}

	public void setEnvironment(String environment) {
		this.environment = environment;
	}

	public String getIp_address() {
		return ip_address;
	}

	public void setIp_address(String ip_address) {
		this.ip_address = ip_address;
	}

	public CpuUsageRecord(String hostname, double systemPct, double userPct, double ioWaitPct, double stealPct,
			double nicePct, double totalPct, double idlePct, String environment, String ip_address) {
		super();
		this.hostname = hostname;
		this.systemPct = systemPct;
		this.userPct = userPct;
		this.ioWaitPct = ioWaitPct;
		this.stealPct = stealPct;
		this.nicePct = nicePct;
		this.totalPct = totalPct;
		this.idlePct = idlePct;
		this.environment = environment;
		this.ip_address = ip_address;
	}

	@Override
	public String toString() {
		return "CpuUsageRecord [hostname=" + hostname + ", systemPct=" + systemPct + ", userPct=" + userPct
				+ ", ioWaitPct=" + ioWaitPct + ", stealPct=" + stealPct + ", nicePct=" + nicePct + ", totalPct="
				+ totalPct + ", idlePct=" + idlePct + ", environment=" + environment + ", ip_address=" + ip_address
				+ "]";
	}

}