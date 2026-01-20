package com.sushil.elasticsearch.model;

public class BackupSummary {
	private String backupType;
	private String date;
	private String timestamp;

	public BackupSummary(String backupType, String date, String timestamp) {
		this.backupType = backupType;
		this.date = date;
		this.timestamp = timestamp;
	}

	public String getBackupType() {
		return backupType;
	}

	public String getDate() {
		return date;
	}

	public String getTimestamp() {
		return timestamp;
	}

	@Override
	public String toString() {
		return "BackupSummary [backupType=" + backupType + ", date=" + date + ", timestamp=" + timestamp + "]";
	}
}
