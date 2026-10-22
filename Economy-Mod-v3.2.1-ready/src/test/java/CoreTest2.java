import com.thaimoney.core.*;
import java.util.*;

public class CoreTest2 {
    static int fails = 0;
    static void check(String n, boolean ok) { System.out.println((ok ? "PASS " : "FAIL ") + n); if (!ok) fails++; }
    interface Thrower { void run() throws Exception; }
    static boolean bankEx(Thrower t) { try { t.run(); return false; } catch (BankException e) { return true; } catch (Exception e) { return false; } }

    public static void main(String[] a) throws Exception {
        // ---- PIN ----
        PinStore pins = new PinStore(3, 5);
        UUID u = UUID.randomUUID();
        check("pin not set", pins.verify(u, "1234", 0) == PinStore.Result.NOT_SET);
        check("pin invalid rejected", bankEx(() -> pins.set(u, "12a4")) && bankEx(() -> pins.set(u, "123")));
        pins.set(u, "4321");
        check("pin ok", pins.verify(u, "4321", 0) == PinStore.Result.OK);
        check("pin wrong 1", pins.verify(u, "0000", 0) == PinStore.Result.WRONG && pins.attemptsLeft(u) == 2);
        check("pin wrong 2", pins.verify(u, "0000", 0) == PinStore.Result.WRONG);
        check("pin wrong 3 locks", pins.verify(u, "0000", 1000) == PinStore.Result.LOCKED);
        check("locked even with right pin", pins.verify(u, "4321", 2000) == PinStore.Result.LOCKED);
        check("lock remaining ~5min", pins.lockRemainingMs(u, 1000) == 300_000);
        check("unlock after 5 min", pins.verify(u, "4321", 1000 + 300_001) == PinStore.Result.OK);
        check("success resets fails", pins.attemptsLeft(u) == 3);
        PinStore p2 = new PinStore(3, 5);
        p2.load(pins.snapshot());
        check("pin roundtrip", p2.verify(u, "4321", 1_000_000) == PinStore.Result.OK);
        check("hash not plaintext", !pins.snapshot().get(u).hash.contains("4321"));

        // ---- Wallet ----
        Wallets w = new Wallets();
        w.add(u, Denomination.NOTE_100, 5);
        w.add(u, Denomination.COIN_1, 7);
        check("wallet total", w.total(u) == 507);
        check("wallet take ok", w.take(u, Denomination.NOTE_100, 2) && w.count(u, Denomination.NOTE_100) == 3);
        check("wallet take too many fails", !w.take(u, Denomination.NOTE_100, 4) && w.count(u, Denomination.NOTE_100) == 3);
        check("wallet take 0/neg fails", !w.take(u, Denomination.COIN_1, 0) && !w.take(u, Denomination.COIN_1, -3));
        Wallets w2 = new Wallets();
        w2.load(w.snapshot());
        check("wallet roundtrip", w2.total(u) == w.total(u));
        w.clear(u);
        check("wallet clear", w.total(u) == 0);

        // ---- History ----
        BankConfig cfg = new BankConfig();
        Bank bank = new Bank(cfg);
        long[] t = {1000};
        bank.setClock(() -> t[0]++);
        UUID s1 = UUID.randomUUID(), b1 = UUID.randomUUID();
        bank.deposit(b1, 5000);
        bank.withdraw(b1, 100, 1);
        List<String> h = bank.history(b1);
        check("history newest first", h.size() == 2 && h.get(0).contains("ถอน") && h.get(1).contains("ฝาก"));
        for (int i = 0; i < 50; i++) bank.deposit(b1, 1);
        check("history capped at 30", bank.history(b1).size() == 30);
        Bank bk2 = new Bank(cfg);
        bk2.loadHistory(bank.snapshotHistory());
        check("history roundtrip", bk2.history(b1).size() == 30);

        // ---- Market ----
        Market m = new Market();
        var l1 = m.create(s1, "Seller", "minecraft:cobblestone", 64, 200, 2, 0, 1_000_000);
        check("listing created id=1", l1.id == 1);
        check("bad price rejected", bankEx(() -> m.create(s1, "S", "minecraft:dirt", 1, 0, 2, 0, 1_000_000))
                && bankEx(() -> m.create(s1, "S", "minecraft:dirt", 1, -5, 2, 0, 1_000_000)));
        m.create(s1, "Seller", "minecraft:dirt", 1, 5, 2, 0, 1_000_000);
        check("max listings enforced", bankEx(() -> m.create(s1, "S", "minecraft:sand", 1, 5, 2, 0, 1_000_000)));
        long before = bank.balance(b1);
        m.buy(bank, 1, b1, cfg.saleTaxBp);
        check("buyer paid full", bank.balance(b1) == before - 200);
        check("seller got price minus 3% tax", bank.balance(s1) == 194);
        check("tax to treasury", bank.balance(Bank.TREASURY) >= 6);
        check("listing removed after buy", m.get(1) == null);
        check("cannot buy twice", bankEx(() -> m.buy(bank, 1, b1, cfg.saleTaxBp)));
        check("cannot buy own", bankEx(() -> m.buy(bank, 2, s1, cfg.saleTaxBp)));
        UUID poor = UUID.randomUUID();
        check("insufficient funds keeps listing", bankEx(() -> m.buy(bank, 2, poor, cfg.saleTaxBp)) && m.get(2) != null);
        check("cancel by non-owner rejected", bankEx(() -> m.cancel(2, b1)));
        check("cancel by owner ok", m.cancel(2, s1).id == 2 && m.get(2) == null);
        Market m2 = new Market();
        m2.load(m.snapshot(), m.nextId());
        check("market ids continue after load", m2.create(s1, "S", "minecraft:dirt", 1, 5, 5, 0, 100).id == m.nextId());

        // ---- Spend/Earn ----
        long bal = bank.balance(b1);
        bank.spend(b1, 100, "shop");
        bank.earn(b1, 40, "shop sell");
        check("spend/earn", bank.balance(b1) == bal - 60);
        check("spend insufficient", bankEx(() -> bank.spend(poor, 10, "x")));

        // ---- Shop pricing ----
        ShopPricing sp = new ShopPricing(5000, 500);
        ShopPricing.Entry e = new ShopPricing.Entry("minecraft:iron_ingot", 100, 1000);
        long now = 0;
        check("base buy price = price at target", sp.unitBuyNow(e, now) == 100);
        check("base sell price = 50%", sp.unitSellNow(e, now) == 50);
        long buy10 = sp.quoteBuy(e, 10, now);
        check("buying many costs >= flat", buy10 >= 1000);
        sp.applyBuy(e, 500, now);
        long afterBuy = sp.unitBuyNow(e, now);
        check("buying raises price", afterBuy > 100);
        sp.applySell(e, 1500, now);
        check("selling lowers price", sp.unitBuyNow(e, now) < 100);
        check("price floor 0.5x", sp.unitBuyNow(new ShopPricing.Entry("x", 100, 10), now) == 100); // fresh = target
        sp.applySell(e, 1_000_000, now);
        check("buy price floor at 50", sp.unitBuyNow(e, now) == 50);
        // ไม่มี arbitrage: ซื้อ n แล้วขายคืน n ต้องไม่กำไร
        ShopPricing sp2 = new ShopPricing(5000, 500);
        ShopPricing.Entry e2 = new ShopPricing.Entry("minecraft:diamond", 400, 200);
        boolean noArb = true;
        Random r = new Random(7);
        for (int i = 0; i < 2000; i++) {
            int n = 1 + r.nextInt(64);
            long cost = sp2.quoteBuy(e2, n, now);
            sp2.applyBuy(e2, n, now);
            long back = sp2.quoteSell(e2, n, now);
            sp2.applySell(e2, n, now);
            if (back > cost) noArb = false;
        }
        check("no arbitrage buy→sell", noArb);
        // ฟื้นตัวตามเวลา
        ShopPricing sp3 = new ShopPricing(5000, 500);
        sp3.applySell(e2, 5000, 0);
        double s0 = sp3.stockNow(e2, 0);
        double s24 = sp3.stockNow(e2, 24 * 3_600_000L);
        check("stock recovers toward target", s24 < s0 && s24 > e2.target);
        ShopPricing sp4 = new ShopPricing(5000, 500);
        sp4.load(sp3.snapshot());
        check("stock roundtrip", Math.abs(sp4.stockNow(e2, 24 * 3_600_000L) - s24) < 1e-6);

        System.out.println(fails == 0 ? "ALL PASSED" : (fails + " FAILED"));
        System.exit(fails == 0 ? 0 : 1);
    }
}
