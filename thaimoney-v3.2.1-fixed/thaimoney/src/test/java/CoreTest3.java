import com.thaimoney.core.*;
import java.util.*;

public class CoreTest3 {
    static int fails = 0;
    static void check(String n, boolean ok) { System.out.println((ok ? "PASS " : "FAIL ") + n); if (!ok) fails++; }
    interface Thrower { void run() throws Exception; }
    static boolean bankEx(Thrower t) { try { t.run(); return false; } catch (BankException e) { return true; } catch (Exception e) { return false; } }
    static long total(Bank b, UUID... ids) { long t = b.balance(Bank.TREASURY); for (UUID i : ids) t += b.balance(i); return t; }

    public static void main(String[] args) throws Exception {
        BankConfig cfg = new BankConfig();
        Bank bank = new Bank(cfg);
        Mailbox mail = new Mailbox();
        Auctions au = new Auctions();
        UUID seller = UUID.randomUUID(), b1 = UUID.randomUUID(), b2 = UUID.randomUUID(), poor = UUID.randomUUID();
        bank.deposit(b1, 10_000); bank.deposit(b2, 10_000);
        long start = total(bank, seller, b1, b2, poor);
        long t0 = 1_000_000;

        var a = au.create(seller, "S", "minecraft:diamond", 3, 100, 10 * 60_000, t0, 3, 1_000_000);
        check("auction created", a.id == 1 && a.endsAt == t0 + 600_000);
        check("duration < 1 min rejected", bankEx(() -> au.create(seller, "S", "x", 1, 5, 1000, t0, 3, 100)));
        check("seller cannot bid", bankEx(() -> au.bid(bank, 1, seller, "S", 100, t0)));
        check("bid below start rejected", bankEx(() -> au.bid(bank, 1, b1, "B1", 99, t0)));
        check("insufficient funds rejected", bankEx(() -> au.bid(bank, 1, poor, "P", 100, t0)));
        au.bid(bank, 1, b1, "B1", 100, t0);
        check("first bid holds money", bank.balance(b1) == 9_900 && a.currentBid == 100);
        check("min next bid +5%", Auctions.minNextBid(a) == 105);
        check("bid below +5% rejected", bankEx(() -> au.bid(bank, 1, b2, "B2", 104, t0)));
        au.bid(bank, 1, b2, "B2", 200, t0 + 1000);
        check("outbid refunds previous", bank.balance(b1) == 10_000 && bank.balance(b2) == 9_800);
        au.bid(bank, 1, b2, "B2", 300, t0 + 2000);
        check("same bidder pays only the difference", bank.balance(b2) == 9_700);
        check("bid after end rejected", bankEx(() -> au.bid(bank, 1, b1, "B1", 1000, t0 + 600_000)));
        // ต่อเวลา: เสนอในช่วง 2 นาทีสุดท้าย
        long near = t0 + 600_000 - 30_000;
        au.bid(bank, 1, b1, "B1", 400, near);
        check("anti-snipe extends to 2 min", a.endsAt == near + Auctions.EXTEND_WINDOW_MS);
        check("not settled early", au.settleDue(bank, mail, near + 1000, cfg.auctionFeeBp).isEmpty());
        var res = au.settleDue(bank, mail, a.endsAt, cfg.auctionFeeBp);
        check("settled once", res.size() == 1 && res.get(0).sold() && res.get(0).fee() == 20);
        check("seller paid minus 5% fee", bank.balance(seller) == 380);
        check("winner gets item via mailbox", mail.pending(b1) == 3 && mail.pending(b2) == 0);
        check("loser fully refunded", bank.balance(b2) == 10_000);
        check("money conserved through auction", total(bank, seller, b1, b2, poor) == start);
        check("auction removed", au.get(1) == null);

        // ไม่มีผู้ประมูล → คืนของผู้ขาย
        var a2 = au.create(seller, "S", "minecraft:dirt", 5, 10, 60_000, t0, 3, 1000);
        var r2 = au.settleDue(bank, mail, t0 + 60_000, cfg.auctionFeeBp);
        check("no bids → item back to seller", r2.size() == 1 && !r2.get(0).sold() && mail.pending(seller) == 5);

        // ยกเลิก
        var a3 = au.create(seller, "S", "minecraft:sand", 2, 10, 60_000, t0, 3, 1000);
        check("cancel by non-owner rejected", bankEx(() -> au.cancel(a3.id, b1, mail)));
        au.bid(bank, a3.id, b1, "B1", 10, t0);
        check("cancel with bids rejected", bankEx(() -> au.cancel(a3.id, seller, mail)));
        var a4 = au.create(seller, "S", "minecraft:gravel", 2, 10, 60_000, t0, 3, 1000);
        au.cancel(a4.id, seller, mail);
        check("cancel ok returns item", mail.pending(seller) == 5 + 2);
        check("max auctions enforced", bankEx(() -> { au.create(seller, "S", "m", 1, 5, 60_000, t0, 1, 1000); }));

        // snapshot/load
        Auctions au2 = new Auctions();
        au2.load(au.snapshot(), au.nextId());
        check("auction ids continue after load", au2.create(seller, "S", "minecraft:dirt", 1, 5, 60_000, t0, 99, 1000).id == au.nextId());

        // ---- Mailbox ----
        Mailbox m2 = new Mailbox();
        m2.load(mail.snapshot());
        check("mailbox roundtrip", m2.pending(b1) == 3);
        check("mailbox drain empties", mail.drain(b1).size() == 1 && mail.pending(b1) == 0);

        // ---- Stalls ----
        Stalls st = new Stalls();
        long now = 5_000_000_000L;
        bank.deposit(b1, 100_000);
        long before = bank.balance(b1);
        check("stall inactive by default", !st.active(b1, now));
        st.rent(bank, b1, 1, cfg.stallRentPerDay, now);
        check("stall rent charged to treasury", bank.balance(b1) == before - 500 && st.active(b1, now));
        check("stall expires after 1 day", st.active(b1, now + Stalls.DAY_MS - 1) && !st.active(b1, now + Stalls.DAY_MS));
        st.rent(bank, b1, 7, cfg.stallRentPerWeek, now + 1000);
        check("rent extends from expiry", st.expiresAt(b1) == now + 8 * Stalls.DAY_MS);
        check("prepaid cap 30 days", bankEx(() -> st.rent(bank, b1, 30, 100, now)));
        check("rent insufficient funds", bankEx(() -> st.rent(bank, poor, 1, 500, now)));
        long tb = total(bank, seller, b1, b2, poor);
        check("money conserved through stalls (10 still in auction escrow)", tb == start + 100_000 - 10);
        Stalls st2 = new Stalls();
        st2.load(st.snapshot());
        check("stalls roundtrip", st2.expiresAt(b1) == st.expiresAt(b1));

        // ---- Chest shops ----
        ChestShops cs = new ChestShops();
        String k = ChestShops.key("overworld", 1, 64, 2);
        var s = cs.create(seller, "S", k, "minecraft:bread", 10, 2, 1_000_000);
        check("chest shop created", cs.get(k) == s);
        check("duplicate chest rejected", bankEx(() -> cs.create(b1, "B", k, "minecraft:bread", 10, 2, 1000)));
        check("bad price rejected", bankEx(() -> cs.create(seller, "S", "k2", "m", 0, 2, 1000)));
        cs.create(seller, "S", "k2", "m", 1, 2, 1000);
        check("max shops per owner", bankEx(() -> cs.create(seller, "S", "k3", "m", 1, 2, 1000)));
        ChestShops cs2 = new ChestShops();
        cs2.load(cs.snapshot());
        check("chest shops roundtrip", cs2.get(k) != null && cs2.byOwner(seller).size() == 2);
        check("remove works", cs.remove(k) != null && cs.get(k) == null);

        System.out.println(fails == 0 ? "ALL PASSED" : (fails + " FAILED"));
        System.exit(fails == 0 ? 0 : 1);
    }
}
