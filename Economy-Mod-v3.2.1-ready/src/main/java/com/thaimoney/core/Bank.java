package com.thaimoney.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * บัญชีเงินฝั่งเซิร์ฟเวอร์ ผูกกับ UUID ผู้เล่น ใช้จำนวนเต็ม (long) เท่านั้น
 * ไม่มีโค้ด Minecraft ในคลาสนี้
 */
public final class Bank {
    /** บัญชี "กองกลางเซิร์ฟเวอร์" สำหรับรับค่าธรรมเนียม/ภาษี */
    public static final UUID TREASURY = new UUID(0L, 0L);

    private final BankConfig cfg;
    private final Map<UUID, Long> balances = new HashMap<>();
    /** {epochDay, ยอดที่ถอนไปแล้ววันนั้น} */
    private final Map<UUID, long[]> daily = new HashMap<>();
    private final Map<UUID, List<String>> history = new HashMap<>();
    private static final int HISTORY_MAX = 30;
    private Consumer<String> logger = s -> {};
    private LongSupplier clock = System::currentTimeMillis;

    public void setClock(LongSupplier clock) {
        this.clock = clock;
    }

    private void note(UUID id, long delta, String label) {
        if (id.equals(TREASURY)) return;
        List<String> list = history.computeIfAbsent(id, k -> new ArrayList<>());
        list.add(clock.getAsLong() + "|" + delta + "|" + label.replace('|', '/'));
        while (list.size() > HISTORY_MAX) list.remove(0);
    }

    /** ประวัติล่าสุดก่อน รูปแบบแต่ละบรรทัด "epochMillis|delta|label" */
    public List<String> history(UUID id) {
        List<String> copy = new ArrayList<>(history.getOrDefault(id, List.of()));
        java.util.Collections.reverse(copy);
        return copy;
    }

    public Bank(BankConfig cfg) {
        this.cfg = cfg;
    }

    public void setLogger(Consumer<String> logger) {
        this.logger = logger;
    }

    public BankConfig config() {
        return cfg;
    }

    public long balance(UUID id) {
        return balances.getOrDefault(id, id.equals(TREASURY) ? 0L : cfg.startingBalance);
    }

    public long withdrawnToday(UUID id, long today) {
        long[] u = daily.get(id);
        return (u != null && u[0] == today) ? u[1] : 0L;
    }

    public static long fee(long amount, int bp) {
        return Math.multiplyExact(amount, (long) bp) / 10_000L;
    }

    private void checkAmount(long amount) throws BankException {
        if (amount <= 0) throw new BankException("จำนวนเงินต้องเป็นจำนวนเต็มบวก");
        if (amount > cfg.maxTransaction) throw new BankException("จำนวนเงินเกินวงเงินต่อรายการ");
    }

    private long checkedBalance(UUID id, long delta) throws BankException {
        try {
            long result = Math.addExact(balance(id), delta);
            if (result < 0) throw new BankException("ยอดเงินไม่เพียงพอ");
            return result;
        } catch (ArithmeticException e) {
            throw new BankException("ยอดเงินเกินขีดจำกัดของระบบ");
        }
    }

    private void add(UUID id, long delta) throws BankException {
        balances.put(id, checkedBalance(id, delta));
    }

    private static long safeTotal(long amount, long fee) throws BankException {
        try {
            return Math.addExact(amount, fee);
        } catch (ArithmeticException e) {
            throw new BankException("จำนวนเงินเกินขีดจำกัดของระบบ");
        }
    }

    /** ฝากเงิน (ผู้เรียกต้องหักเงินสดจากผู้เล่นเอง) */
    public void deposit(UUID id, long amount) throws BankException {
        checkAmount(amount);
        add(id, amount);
        note(id, amount, "ฝากเงิน");
        logger.accept("DEPOSIT " + id + " +" + amount);
    }

    /** ถอนเงิน: หักยอด + ค่าธรรมเนียม คืนค่าธรรมเนียมที่เก็บ */
    public long withdraw(UUID id, long amount, long today) throws BankException {
        checkAmount(amount);
        long fee = fee(amount, cfg.withdrawFeeBp);
        long total = safeTotal(amount, fee);
        if (balance(id) < total) throw new BankException("ยอดเงินไม่พอ (ต้องใช้ " + total + " รวมค่าธรรมเนียม " + fee + ")");
        long used = withdrawnToday(id, today);
        final long newUsed;
        try { newUsed = Math.addExact(used, amount); }
        catch (ArithmeticException e) { throw new BankException("เกินวงเงินถอนต่อวัน"); }
        if (newUsed > cfg.dailyWithdrawLimit) {
            throw new BankException("เกินวงเงินถอนต่อวัน (เหลือ " + Math.max(0, cfg.dailyWithdrawLimit - used) + ")");
        }
        // ตรวจทุกยอดก่อนแก้ไข เพื่อไม่ให้เกิด giao dịchครึ่งเดียวเมื่อยอดกองกลางล้น
        long newBalance = checkedBalance(id, -total);
        long newTreasury = checkedBalance(TREASURY, fee);
        balances.put(id, newBalance);
        balances.put(TREASURY, newTreasury);
        daily.put(id, new long[] {today, newUsed});
        note(id, -total, "ถอนเงิน (ค่าธรรมเนียม " + fee + ")");
        logger.accept("WITHDRAW " + id + " -" + amount + " fee=" + fee);
        return fee;
    }

    /** โอนเงิน: ผู้โอนจ่าย amount + ค่าธรรมเนียม ผู้รับได้ amount เต็ม คืนค่าธรรมเนียม */
    public long transfer(UUID from, UUID to, long amount) throws BankException {
        return transfer(from, to, amount, "โอนออก", "รับโอน");
    }

    public long transfer(UUID from, UUID to, long amount, String fromLabel, String toLabel) throws BankException {
        checkAmount(amount);
        if (from.equals(to)) throw new BankException("โอนให้ตัวเองไม่ได้");
        long fee = fee(amount, cfg.transferFeeBp);
        long total = safeTotal(amount, fee);
        if (balance(from) < total) throw new BankException("ยอดเงินไม่พอ (ต้องใช้ " + total + " รวมค่าธรรมเนียม " + fee + ")");
        // Preflight ทุกบัญชีก่อน commit ป้องกันการหักเงินแล้วแต่ผู้รับไม่ได้รับเงิน
        long newFrom = checkedBalance(from, -total);
        long newTo = checkedBalance(to, amount);
        long newTreasury = checkedBalance(TREASURY, fee);
        balances.put(from, newFrom);
        balances.put(to, newTo);
        balances.put(TREASURY, newTreasury);
        note(from, -total, fromLabel + " (ค่าธรรมเนียม " + fee + ")");
        note(to, amount, toLabel);
        logger.accept("TRANSFER " + from + " -> " + to + " " + amount + " fee=" + fee);
        return fee;
    }

    /** ซื้อขายระหว่างผู้เล่น: ผู้ซื้อจ่ายเต็ม ผู้ขายได้หักภาษี ภาษีเข้ากองกลาง คืนค่าภาษี */
    public long sale(UUID buyer, UUID seller, long price, int taxBp, String buyLabel, String sellLabel) throws BankException {
        checkAmount(price);
        if (buyer.equals(seller)) throw new BankException("ซื้อของตัวเองไม่ได้");
        if (balance(buyer) < price) throw new BankException("ยอดเงินไม่พอ (ต้องใช้ " + price + ")");
        long tax = fee(price, taxBp);
        long newBuyer = checkedBalance(buyer, -price);
        long newSeller = checkedBalance(seller, price - tax);
        long newTreasury = checkedBalance(TREASURY, tax);
        balances.put(buyer, newBuyer);
        balances.put(seller, newSeller);
        balances.put(TREASURY, newTreasury);
        note(buyer, -price, buyLabel);
        note(seller, price - tax, sellLabel + " (ภาษี " + tax + ")");
        logger.accept("SALE " + buyer + " -> " + seller + " " + price + " tax=" + tax);
        return tax;
    }

    /** ใช้จ่ายให้ระบบ (เงินหายออกจากระบบ เช่น ซื้อจากร้านระบบ) */
    public void spend(UUID id, long amount, String label) throws BankException {
        checkAmount(amount);
        if (balance(id) < amount) throw new BankException("ยอดเงินไม่พอ (ต้องใช้ " + amount + ")");
        add(id, -amount);
        note(id, -amount, label);
        logger.accept("SPEND " + id + " -" + amount + " " + label);
    }

    /** รับเงินจากระบบ (เงินเข้าระบบ เช่น ขายให้ร้านระบบ) */
    public void earn(UUID id, long amount, String label) throws BankException {
        checkAmount(amount);
        add(id, amount);
        note(id, amount, label);
        logger.accept("EARN " + id + " +" + amount + " " + label);
    }

    // ---- แอดมิน ----
    public void adminGive(UUID id, long amount) throws BankException {
        checkAmount(amount);
        add(id, amount);
        logger.accept("ADMIN_GIVE " + id + " +" + amount);
    }

    public void adminTake(UUID id, long amount) throws BankException {
        checkAmount(amount);
        long cur = balance(id);
        add(id, -Math.min(cur, amount));
        logger.accept("ADMIN_TAKE " + id + " -" + Math.min(cur, amount));
    }

    public void adminSet(UUID id, long amount) throws BankException {
        if (amount < 0 || amount > cfg.maxTransaction * 1000) throw new BankException("ยอดไม่ถูกต้อง");
        balances.put(id, amount);
        logger.accept("ADMIN_SET " + id + " =" + amount);
    }

    // ---- สำหรับบันทึก/โหลดไฟล์ ----
    public Map<UUID, Long> snapshotBalances() {
        return new HashMap<>(balances);
    }

    public Map<UUID, long[]> snapshotDaily() {
        return new HashMap<>(daily);
    }

    public Map<UUID, List<String>> snapshotHistory() {
        Map<UUID, List<String>> copy = new HashMap<>();
        history.forEach((k, v) -> copy.put(k, new ArrayList<>(v)));
        return copy;
    }

    public void loadHistory(Map<UUID, List<String>> h) {
        history.clear();
        h.forEach((k, v) -> history.put(k, new ArrayList<>(v)));
    }

    public void load(Map<UUID, Long> b, Map<UUID, long[]> d) {
        balances.clear();
        balances.putAll(b);
        daily.clear();
        daily.putAll(d);
    }
}
