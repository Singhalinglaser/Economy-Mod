package com.thaimoney.core;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** แผงตลาด: เช่ารายวัน/รายสัปดาห์ ระหว่างเช่าลงขายในตลาดได้มากขึ้นและเสียภาษีขายต่ำลง ค่าเช่าเข้ากองกลาง */
public final class Stalls {
    public static final long DAY_MS = 86_400_000L;
    public static final long MAX_PREPAID_MS = 30 * DAY_MS;

    private final Map<UUID, Long> expires = new HashMap<>();

    public boolean active(UUID id, long now) {
        return expires.getOrDefault(id, 0L) > now;
    }

    public long expiresAt(UUID id) {
        return expires.getOrDefault(id, 0L);
    }

    /** เช่าเพิ่ม days วัน (ต่ออายุต่อจากวันหมดอายุเดิม) ราคาตามที่ผู้เรียกกำหนด */
    public void rent(Bank bank, UUID id, int days, long price, long now) throws BankException {
        if (days <= 0) throw new BankException("จำนวนวันไม่ถูกต้อง");
        long base = Math.max(now, expires.getOrDefault(id, 0L));
        long newEnd = base + days * DAY_MS;
        if (newEnd - now > MAX_PREPAID_MS) throw new BankException("จ่ายล่วงหน้าได้สูงสุด 30 วัน");
        bank.spend(id, price, "เช่าแผงตลาด " + days + " วัน");
        bank.earn(Bank.TREASURY, price, "ค่าเช่าแผงตลาด");
        expires.put(id, newEnd);
    }

    public Map<UUID, Long> snapshot() {
        return new HashMap<>(expires);
    }

    public void load(Map<UUID, Long> in) {
        expires.clear();
        expires.putAll(in);
    }
}
