package com.thaimoney.core;

import java.util.HashMap;
import java.util.Map;

/**
 * ราคาร้านระบบตามอุปสงค์/อุปทาน
 * ตัวคูณราคา f = clamp(2*target/(stock+target), 0.5, 2.0)  (stock = target → f = 1)
 * ผู้เล่นซื้อ → สต็อกลด → ราคาขึ้น / ผู้เล่นขาย → สต็อกเพิ่ม → ราคาลง
 * สต็อกฟื้นเข้าหาค่ากลางตามเวลา ราคารับซื้อ = ราคาขายที่ตัวคูณเดียวกัน × sellRatio (ซื้อแล้วขายคืนจึงขาดทุนเสมอ)
 */
public final class ShopPricing {
    /** รายการในแคตตาล็อก (config) */
    public static class Entry {
        public String item;
        public long price;
        public int target;

        public Entry() {}

        public Entry(String item, long price, int target) {
            this.item = item;
            this.price = price;
            this.target = target;
        }
    }

    /** สถานะสต็อกเสมือน (บันทึกลงไฟล์) */
    public static class Stock {
        public double stock;
        public long updatedAt;
    }

    private final Map<String, Stock> stocks = new HashMap<>();
    private final double sellRatio;
    private final double recoveryPerHour;

    public ShopPricing(int sellRatioBp, int recoveryBpPerHour) {
        this.sellRatio = sellRatioBp / 10_000.0;
        this.recoveryPerHour = recoveryBpPerHour / 10_000.0;
    }

    static double factor(double stock, int target) {
        double f = 2.0 * target / (stock + target);
        return Math.max(0.5, Math.min(2.0, f));
    }

    private Stock current(Entry e, long now) {
        Stock s = stocks.get(e.item);
        if (s == null) {
            s = new Stock();
            s.stock = e.target;
            s.updatedAt = now;
            stocks.put(e.item, s);
            return s;
        }
        double hours = Math.max(0, now - s.updatedAt) / 3_600_000.0;
        if (hours > 0) {
            s.stock = e.target + (s.stock - e.target) * Math.pow(1.0 - recoveryPerHour, hours);
            s.updatedAt = now;
        }
        return s;
    }

    private long unitBuy(Entry e, double stock) {
        return Math.max(1, (long) Math.ceil(e.price * factor(stock, e.target)));
    }

    private long unitSell(Entry e, double stock) {
        return Math.max(1, (long) Math.floor(e.price * sellRatio * factor(stock, e.target)));
    }

    public long quoteBuy(Entry e, int n, long now) {
        double st = current(e, now).stock;
        long sum = 0;
        for (int i = 0; i < n; i++) sum += unitBuy(e, Math.max(0, st - i));
        return sum;
    }

    public long quoteSell(Entry e, int n, long now) {
        double st = current(e, now).stock;
        long sum = 0;
        for (int i = 0; i < n; i++) sum += unitSell(e, st + i);
        return sum;
    }

    /** ราคาต่อชิ้นตอนนี้ (สำหรับแสดงผล) */
    public long unitBuyNow(Entry e, long now) {
        return unitBuy(e, current(e, now).stock);
    }

    public long unitSellNow(Entry e, long now) {
        return unitSell(e, current(e, now).stock);
    }

    public double stockNow(Entry e, long now) {
        return current(e, now).stock;
    }

    /** ใช้หลังจ่ายเงินสำเร็จ: ปรับสต็อกตามจำนวนที่ซื้อ */
    public void applyBuy(Entry e, int n, long now) {
        Stock s = current(e, now);
        s.stock = Math.max(0, s.stock - n);
    }

    public void applySell(Entry e, int n, long now) {
        Stock s = current(e, now);
        s.stock = s.stock + n;
    }

    public Map<String, Stock> snapshot() {
        return new HashMap<>(stocks);
    }

    public void load(Map<String, Stock> in) {
        stocks.clear();
        stocks.putAll(in);
    }
}
