package com.sushil.elasticsearch.model.memory;

public class MemoryUsageRecord {
	private String hostname;
	private String timestamp;
	private String memTotal;
	private String cachedTotal;
	private String memActualUsed;
	private String memActualFree;
	private String memActualUsedPct;
	private String memPhyUsed;
	private String memPhyFree;
	private String memPhyUsedPct;
	private String environment;
	private String ip_address;

	public MemoryUsageRecord() {
		super();
		// TODO Auto-generated constructor stub
	}

	public MemoryUsageRecord(String hostname, String timestamp, String memTotal, String cachedTotal,
			String memActualUsed, String memActualFree, String memActualUsedPct, String memPhyUsed, String memPhyFree,
			String memPhyUsedPct, String environment, String ip_address) {
		super();
		this.hostname = hostname;
		this.timestamp = timestamp;
		this.memTotal = memTotal;
		this.cachedTotal = cachedTotal;
		this.memActualUsed = memActualUsed;
		this.memActualFree = memActualFree;
		this.memActualUsedPct = memActualUsedPct;
		this.memPhyUsed = memPhyUsed;
		this.memPhyFree = memPhyFree;
		this.memPhyUsedPct = memPhyUsedPct;
		this.environment = environment;
		this.ip_address = ip_address;
	}

	public MemoryUsageRecord(String hostname, String timestamp, String memTotal, String cachedTotal,
			String memActualUsed, String memActualFree, String memActualUsedPct, String memPhyUsed, String memPhyFree,
			String memPhyUsedPct) {
		super();
		this.hostname = hostname;
		this.timestamp = timestamp;
		this.memTotal = memTotal;
		this.cachedTotal = cachedTotal;
		this.memActualUsed = memActualUsed;
		this.memActualFree = memActualFree;
		this.memActualUsedPct = memActualUsedPct;
		this.memPhyUsed = memPhyUsed;
		this.memPhyFree = memPhyFree;
		this.memPhyUsedPct = memPhyUsedPct;
	}

	public String getHostname() {
		return hostname;
	}

	public void setHostname(String hostname) {
		this.hostname = hostname;
	}

	public String getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(String timestamp) {
		this.timestamp = timestamp;
	}

	public String getMemTotal() {
		return memTotal;
	}

	public void setMemTotal(String memTotal) {
		this.memTotal = memTotal;
	}

	public String getCachedTotal() {
		return cachedTotal;
	}

	public void setCachedTotal(String cachedTotal) {
		this.cachedTotal = cachedTotal;
	}

	public String getMemActualUsed() {
		return memActualUsed;
	}

	public void setMemActualUsed(String memActualUsed) {
		this.memActualUsed = memActualUsed;
	}

	public String getMemActualFree() {
		return memActualFree;
	}

	public void setMemActualFree(String memActualFree) {
		this.memActualFree = memActualFree;
	}

	public String getMemActualUsedPct() {
		return memActualUsedPct;
	}

	public void setMemActualUsedPct(String memActualUsedPct) {
		this.memActualUsedPct = memActualUsedPct;
	}

	public String getMemPhyUsed() {
		return memPhyUsed;
	}

	public void setMemPhyUsed(String memPhyUsed) {
		this.memPhyUsed = memPhyUsed;
	}

	public String getMemPhyFree() {
		return memPhyFree;
	}

	public void setMemPhyFree(String memPhyFree) {
		this.memPhyFree = memPhyFree;
	}

	public String getMemPhyUsedPct() {
		return memPhyUsedPct;
	}

	public void setMemPhyUsedPct(String memPhyUsedPct) {
		this.memPhyUsedPct = memPhyUsedPct;
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

	@Override
	public String toString() {
		return "MemoryUsageRecord [hostname=" + hostname + ", timestamp=" + timestamp + ", memTotal=" + memTotal
				+ ", cachedTotal=" + cachedTotal + ", memActualUsed=" + memActualUsed + ", memActualFree="
				+ memActualFree + ", memActualUsedPct=" + memActualUsedPct + ", memPhyUsed=" + memPhyUsed
				+ ", memPhyFree=" + memPhyFree + ", memPhyUsedPct=" + memPhyUsedPct + ", environment=" + environment
				+ ", ip_address=" + ip_address + "]";
	}

}