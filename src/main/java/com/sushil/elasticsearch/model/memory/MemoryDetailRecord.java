package com.sushil.elasticsearch.model.memory;

public class MemoryDetailRecord {
    private String hostname;
    private String timestamp;
    private String ipAddress;
    private String environment;    
    private double swapMemoryPct;
    private double actualMemoryPct;
	public MemoryDetailRecord() {
		super();
		// TODO Auto-generated constructor stub
	}

	public MemoryDetailRecord(String hostname, String timestamp, String ipAddress, String environment,
			double swapMemoryPct, double actualMemoryPct) {
		super();
		this.hostname = hostname;
		this.timestamp = timestamp;
		this.ipAddress = ipAddress;
		this.environment = environment;
		this.swapMemoryPct = swapMemoryPct;
		this.actualMemoryPct = actualMemoryPct;
	}


	public MemoryDetailRecord(String hostname, String timestamp, double swapMemoryPct, double actualMemoryPct) {
		super();
		this.hostname = hostname;
		this.timestamp = timestamp;
		this.swapMemoryPct = swapMemoryPct;
		this.actualMemoryPct = actualMemoryPct;
	}

	public String getIpAddress() {
		return ipAddress;
	}

	public void setIpAddress(String ipAddress) {
		this.ipAddress = ipAddress;
	}

	public String getEnvironment() {
		return environment;
	}

	public void setEnvironment(String environment) {
		this.environment = environment;
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
	public double getSwapMemoryPct() {
		return swapMemoryPct;
	}
	public void setSwapMemoryPct(double swapMemoryPct) {
		this.swapMemoryPct = swapMemoryPct;
	}
	public double getActualMemoryPct() {
		return actualMemoryPct;
	}
	public void setActualMemoryPct(double actualMemoryPct) {
		this.actualMemoryPct = actualMemoryPct;
	}

	@Override
	public String toString() {
		return "MemoryDetailRecord [hostname=" + hostname + ", timestamp=" + timestamp + ", ipAddress=" + ipAddress
				+ ", environment=" + environment + ", swapMemoryPct=" + swapMemoryPct + ", actualMemoryPct="
				+ actualMemoryPct + "]";
	}
    
    
}