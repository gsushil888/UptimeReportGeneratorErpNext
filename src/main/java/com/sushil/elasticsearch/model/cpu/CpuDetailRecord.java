package com.sushil.elasticsearch.model.cpu;

import java.time.Instant;

public class CpuDetailRecord {
	private String hostname;
	private Instant timestamp;
	private double userPct;
	private double systemPct;
	private double totalPct;
	private String environment;
	private String ip_address;

	public CpuDetailRecord() {
	}

	public String getHostname() {
		return hostname;
	}

	public void setHostname(String hostname) {
		this.hostname = hostname;
	}

	public Instant getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(Instant timestamp) {
		this.timestamp = timestamp;
	}

	public double getUserPct() {
		return userPct;
	}

	public void setUserPct(double userPct) {
		this.userPct = userPct;
	}

	public double getSystemPct() {
		return systemPct;
	}

	public void setSystemPct(double systemPct) {
		this.systemPct = systemPct;
	}

	public double getTotalPct() {
		return totalPct;
	}

	public void setTotalPct(double totalPct) {
		this.totalPct = totalPct;
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

	public CpuDetailRecord(Instant timestamp, double userPct, double systemPct, double totalPct, String environment,
			String ip_address) {
		super();
		this.timestamp = timestamp;
		this.userPct = userPct;
		this.systemPct = systemPct;
		this.totalPct = totalPct;
		this.environment = environment;
		this.ip_address = ip_address;
	}

	@Override
	public String toString() {
		return "CpuDetailRecord [timestamp=" + timestamp + ", userPct=" + userPct + ", systemPct=" + systemPct
				+ ", totalPct=" + totalPct + ", environment=" + environment + ", ip_address=" + ip_address + "]";
	}

}
