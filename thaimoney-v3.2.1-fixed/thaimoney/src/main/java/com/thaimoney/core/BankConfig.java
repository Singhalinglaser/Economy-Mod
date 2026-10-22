package com.thaimoney.core;

/** ค่าที่ปรับได้ (config/thaimoney.json) หน่วย bp = 1/100 ของเปอร์เซ็นต์ (100 bp = 1%) */
public class BankConfig {
    public int withdrawFeeBp = 100;
    public int transferFeeBp = 100;
    public long dailyWithdrawLimit = 50_000;
    public long startingBalance = 0;
    public long maxTransaction = 1_000_000_000L;

    // PIN
    public int pinMaxAttempts = 3;
    public int pinLockMinutes = 5;
    /** true = /deposit และ /withdraw ใช้ไม่ได้ ต้องไปที่ตู้ ATM */
    public boolean requireAtmForCash = true;

    // ตลาด
    public int saleTaxBp = 300;
    public int maxListingsPerPlayer = 10;

    // ร้านระบบ
    public int shopSellRatioBp = 5000;          // รับซื้อที่ 50% ของราคาขาย
    public int shopRecoveryBpPerHour = 500;     // สต็อกฟื้นเข้าหาค่ากลางชั่วโมงละ 5%

    // ประมูล
    public int auctionFeeBp = 500;
    public int auctionMaxMinutes = 1440;
    public int maxAuctionsPerPlayer = 3;

    // แผงตลาด (เช่ารายวัน/รายสัปดาห์: ลงขายได้มากขึ้น + ภาษีขายต่ำลง)
    public long stallRentPerDay = 500;
    public long stallRentPerWeek = 3000;
    public int stallTaxBp = 100;
    public int stallMaxListings = 30;

    // ตู้ขายของ (หีบ)
    public int chestShopMaxPerPlayer = 10;
}
