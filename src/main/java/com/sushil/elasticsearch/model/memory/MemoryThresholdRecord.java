package com.sushil.elasticsearch.model.memory;

public class MemoryThresholdRecord {
	private String hostname;
	private String startAt;
	private String endAt;
	private Double avgPct;
	private Double maxPct;
	private String environment;
	private String ipAddress;

	public MemoryThresholdRecord() {
		super();
		// TODO Auto-generated constructor stub
	}

	public MemoryThresholdRecord(String hostname, String startAt, String endAt, Double avgPct, Double maxPct,
			String environment,String ipAddress) {
		super();
		this.hostname = hostname;
		this.startAt = startAt;
		this.endAt = endAt;
		this.avgPct = avgPct;
		this.maxPct = maxPct;
		this.environment = environment;
		this.ipAddress=ipAddress;
	}

	public MemoryThresholdRecord(String hostname, String startAt, String endAt, Double avgPct, Double maxPct) {
		super();
		this.hostname = hostname;
		this.startAt = startAt;
		this.endAt = endAt;
		this.avgPct = avgPct;
		this.maxPct = maxPct;
	}

	public String getHostname() {
		return hostname;
	}

	public void setHostname(String hostname) {
		this.hostname = hostname;
	}

	public String getStartAt() {
		return startAt;
	}

	public void setStartAt(String startAt) {
		this.startAt = startAt;
	}

	public String getEndAt() {
		return endAt;
	}

	public void setEndAt(String endAt) {
		this.endAt = endAt;
	}
	

	public String getIpAddress() {
		return ipAddress;
	}

	public void setIpAddress(String ipAddress) {
		this.ipAddress = ipAddress;
	}

	public Double getAvgPct() {
		return avgPct;
	}

	public void setAvgPct(Double avgPct) {
		this.avgPct = avgPct;
	}

	public Double getMaxPct() {
		return maxPct;
	}

	public void setMaxPct(Double maxPct) {
		this.maxPct = maxPct;
	}

	public String getEnvironment() {
		return environment;
	}

	public void setEnvironment(String environment) {
		this.environment = environment;
	}

	@Override
	public String toString() {
		return "MemoryThresholdRecord [hostname=" + hostname + ", startAt=" + startAt + ", endAt=" + endAt + ", avgPct="
				+ avgPct + ", maxPct=" + maxPct + ", environment=" + environment + ", ipAddress=" + ipAddress + "]";
	}


}