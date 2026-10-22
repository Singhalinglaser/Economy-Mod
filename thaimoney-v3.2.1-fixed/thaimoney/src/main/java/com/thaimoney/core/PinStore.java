package com.thaimoney.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/** เก็บ PIN 4 หลักแบบแฮชพร้อม salt และล็อกชั่วคราวเมื่อใส่ผิดครบกำหนด */
public final class PinStore {
    public enum Result { OK, WRONG, LOCKED, NOT_SET }

    /** รูปแบบที่บันทึกลงไฟล์ */
    public static class Record {
        public String salt;
        public String hash;
        public int fails;
        public long lockedUntil;
    }

    private static final SecureRandom RANDOM = new SecureRandom();
    private final Map<UUID, Record> records = new HashMap<>();
    private final int maxAttempts;
    private final long lockMillis;

    public PinStore(int maxAttempts, int lockMinutes) {
        this.maxAttempts = Math.max(1, maxAttempts);
        this.lockMillis = Math.max(1, lockMinutes) * 60_000L;
    }

    public boolean has(UUID id) {
        return records.containsKey(id);
    }

    public static boolean valid(String pin) {
        return pin != null && pin.length() == 4 && pin.chars().allMatch(c -> c >= '0' && c <= '9');
    }

    private static String hash(String salt, String pin) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] cur = (salt + ":" + pin).getBytes(StandardCharsets.UTF_8);
            for (int i = 0; i < 20_000; i++) cur = md.digest(cur);
            return HexFormat.of().formatHex(cur);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public void set(UUID id, String pin) throws BankException {
        if (!valid(pin)) throw new BankException("PIN ต้องเป็นตัวเลข 4 หลัก");
        byte[] s = new byte[16];
        RANDOM.nextBytes(s);
        Record r = new Record();
        r.salt = HexFormat.of().formatHex(s);
        r.hash = hash(r.salt, pin);
        records.put(id, r);
    }

    public Result verify(UUID id, String pin, long now) {
        Record r = records.get(id);
        if (r == null) return Result.NOT_SET;
        if (r.lockedUntil > now) return Result.LOCKED;
        if (valid(pin) && MessageDigest.isEqual(
                hash(r.salt, pin).getBytes(StandardCharsets.UTF_8), r.hash.getBytes(StandardCharsets.UTF_8))) {
            r.fails = 0;
            return Result.OK;
        }
        r.fails++;
        if (r.fails >= maxAttempts) {
            r.fails = 0;
            r.lockedUntil = now + lockMillis;
            return Result.LOCKED;
        }
        return Result.WRONG;
    }

    public int attemptsLeft(UUID id) {
        Record r = records.get(id);
        return r == null ? maxAttempts : Math.max(0, maxAttempts - r.fails);
    }

    public long lockRemainingMs(UUID id, long now) {
        Record r = records.get(id);
        return r == null ? 0 : Math.max(0, r.lockedUntil - now);
    }

    public Map<UUID, Record> snapshot() {
        return new HashMap<>(records);
    }

    public void load(Map<UUID, Record> data) {
        records.clear();
        records.putAll(data);
    }
}
