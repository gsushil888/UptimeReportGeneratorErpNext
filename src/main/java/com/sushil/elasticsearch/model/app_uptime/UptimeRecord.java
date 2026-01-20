package com.sushil.elasticsearch.model.app_uptime;

import java.util.List;

public class UptimeRecord {
    private String url;
    private double avgUptime;
    private List<UptimeInterval> intervals;

    public UptimeRecord() {

    }

    public UptimeRecord(String url, double avgUptime, List<UptimeInterval> intervals) {
        this.url = url;
        this.avgUptime = avgUptime;
        this.intervals = intervals;
    }

    public double getAvgUptime() {
        return avgUptime;
    }

    public List<UptimeInterval> getIntervals() {
        return intervals;
    }

    public String getUrl() {
        return url;
    }

    public void setAvgUptime(double avgUptime) {
        this.avgUptime = avgUptime;
    }

    public void setIntervals(List<UptimeInterval> intervals) {
        this.intervals = intervals;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    @Override
    public String toString() {
        return "UptimeRecord [url=" + url + ", avgUptime=" + avgUptime + ", intervals=" + intervals + "]";
    }
}