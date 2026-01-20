package com.sushil.elasticsearch.model.app_uptime;

import java.time.Instant;

public class UptimeInterval {
    private Instant timestamp;
    private double uptime;

    public UptimeInterval() {

    }

    public UptimeInterval(Instant timestamp, double uptime) {
        this.timestamp = timestamp;
        this.uptime = uptime;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public double getUptime() {
        return uptime;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public void setUptime(double uptime) {
        this.uptime = uptime;
    }

    @Override
    public String toString() {
        return "UptimeInterval [timestamp=" + timestamp + ", uptime=" + uptime + "]";
    }

}