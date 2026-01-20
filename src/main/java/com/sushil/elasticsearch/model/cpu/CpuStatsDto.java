package com.sushil.elasticsearch.model.cpu;

//🔹 CPU Stats DTO
public class CpuStatsDto {
	private double avgPct;
	private double maxPct;

	// Getters & setters
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
}