package com.sushil.elasticsearch.model.cpu;

import java.time.Instant;

//2️⃣ Threshold Exceeded Record
public class CpuThresholdRecord {
	private String hostname;
	private Instant startTime;
	private Instant endTime;
	private double avgPct;
	private double maxPct;
	private String environment;
	private String ip_address;

	public CpuThresholdRecord() {
	}

	public String gethostname() {
		return hostname;
	}

	public void sethostname(String hostname) {
		this.hostname = hostname;
	}

	public Instant getStartTime() {
		return startTime;
	}

	public void setStartTime(Instant startTime) {
		this.startTime = startTime;
	}

	public Instant getEndTime() {
		return endTime;
	}

	public void setEndTime(Instant endTime) {
		this.endTime = endTime;
	}

	public double getAvgPct() {
		return avgPct;
	}

	public void setAvgPct(double avgPct) {
		this.avgPct = avgPct;
	}

	public double getMaxPct() {
		return maxPct;
	}

	public void setMaxPct(double maxPct) {
		this.maxPct = maxPct;
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

	public CpuThresholdRecord(String hostname, Instant startTime, Instant endTime, double avgPct, double maxPct,
			String environment, String ip_address) {
		super();
		this.hostname = hostname;
		this.startTime = startTime;
		this.endTime = endTime;
		this.avgPct = avgPct;
		this.maxPct = maxPct;
		this.environment = environment;
		this.ip_address = ip_address;
	}

	@Override
	public String toString() {
		return "CpuThresholdRecord [hostname=" + hostname + ", startTime=" + startTime + ", endTime=" + endTime
				+ ", avgPct=" + avgPct + ", maxPct=" + maxPct + ", environment=" + environment + ", ip_address="
				+ ip_address + "]";
	}

}
