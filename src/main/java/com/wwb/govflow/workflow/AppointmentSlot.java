package com.wwb.govflow.workflow;

public class AppointmentSlot {
    private String slotId;
    private String itemCode;
    private String slotDate;
    private String period; // 上午(AM) / 下午(PM)
    private int totalQuota;
    private int availableQuota;
    private int version;

    public AppointmentSlot() {}

    public AppointmentSlot(String slotId, String itemCode, String slotDate, String period, int totalQuota, int availableQuota, int version) {
        this.slotId = slotId;
        this.itemCode = itemCode;
        this.slotDate = slotDate;
        this.period = period;
        this.totalQuota = totalQuota;
        this.availableQuota = availableQuota;
        this.version = version;
    }

    public String getSlotId() { return slotId; }
    public void setSlotId(String slotId) { this.slotId = slotId; }

    public String getItemCode() { return itemCode; }
    public void setItemCode(String itemCode) { this.itemCode = itemCode; }

    public String getSlotDate() { return slotDate; }
    public void setSlotDate(String slotDate) { this.slotDate = slotDate; }

    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }

    public int getTotalQuota() { return totalQuota; }
    public void setTotalQuota(int totalQuota) { this.totalQuota = totalQuota; }

    public synchronized int getAvailableQuota() { return availableQuota; }
    public synchronized void setAvailableQuota(int availableQuota) { this.availableQuota = availableQuota; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    /**
     * 原子扣减号源配额 (CAS 防超买防超发)
     */
    public synchronized boolean deductQuota() {
        if (availableQuota > 0) {
            availableQuota--;
            version++;
            return true;
        }
        return false;
    }
}
