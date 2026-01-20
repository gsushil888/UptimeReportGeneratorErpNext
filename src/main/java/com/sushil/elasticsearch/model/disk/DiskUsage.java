package com.sushil.elasticsearch.model.disk;

public class DiskUsage {
    private String hostname;
    private String filesystem;
    private String mountPoint;
    private double usedPct;
    private String available;
    private String used;
    private String total;

    // getters/setters
    public String getHostname() {
        return hostname;
    }

    public String getAvailable() {
        return available;
    }

    public String getFilesystem() {
        return filesystem;
    }

    public String getMountPoint() {
        return mountPoint;
    }

    public String getTotal() {
        return total;
    }

    public String getUsed() {
        return used;
    }

    public double getUsedPct() {
        return usedPct;
    }

    public void setAvailable(String available) {
        this.available = available;
    }

    public void setFilesystem(String filesystem) {
        this.filesystem = filesystem;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public void setMountPoint(String mountPoint) {
        this.mountPoint = mountPoint;
    }

    public void setTotal(String total) {
        this.total = total;
    }

    public void setUsed(String used) {
        this.used = used;
    }

    public void setUsedPct(double usedPct) {
        this.usedPct = usedPct;
    }

    public DiskUsage() {
    }

    public DiskUsage(String hostname, String filesystem, String mountPoint, double usedPct, String available,
            String used,
            String total) {
        this.hostname = hostname;
        this.filesystem = filesystem;
        this.mountPoint = mountPoint;
        this.usedPct = usedPct;
        this.available = available;
        this.used = used;
        this.total = total;
    }

    @Override
    public String toString() {
        return "DiskUsage [hostname=" + hostname + ", filesystem=" + filesystem + ", mountPoint=" + mountPoint
                + ", usedPct=" + usedPct + ", available=" + available + ", used=" + used + ", total=" + total + "]";
    }

}