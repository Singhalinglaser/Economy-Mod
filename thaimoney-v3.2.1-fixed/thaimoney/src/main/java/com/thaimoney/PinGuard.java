package com.thaimoney;

import java.util.UUID;

/** ตรวจ PIN พร้อมข้อความภาษาไทย คืน null เมื่อผ่าน (หรือยังไม่ได้ตั้ง PIN) */
public final class PinGuard {
    private PinGuard() {}

    public static String check(UUID id, String pin) {
        long now = System.currentTimeMillis();
        return switch (MoneyMod.pins().verify(id, pin, now)) {
            case OK, NOT_SET -> null;
            case WRONG -> "§cPIN ไม่ถูกต้อง เหลืออีก " + MoneyMod.pins().attemptsLeft(id) + " ครั้ง";
            case LOCKED -> "§cบัญชีถูกล็อกชั่วคราว ลองใหม่ใน "
                    + Math.max(1, (MoneyMod.pins().lockRemainingMs(id, now) + 59_999) / 60_000) + " นาที";
        };
    }

    public static boolean isLocked(UUID id) {
        return MoneyMod.pins().lockRemainingMs(id, System.currentTimeMillis()) > 0;
    }
}
