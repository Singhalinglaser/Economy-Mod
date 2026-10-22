import com.thaimoney.core.*;
import java.util.*;

public class CoreTest {
    static int fails = 0;
    static void check(String name, boolean ok) {
        System.out.println((ok ? "PASS " : "FAIL ") + name);
        if (!ok) fails++;
    }
    static boolean throwsBank(Runnable0 r) {
        try { r.run(); return false; } catch (BankException e) { return true; } catch (Exception e) { return false; }
    }
    interface Runnable0 { void run() throws Exception; }

    public static void main(String[] a) throws Exception {
        // ตัวอย่างในเอกสาร: 1,688 = 1000+500+100+50+20+10+5+2+1
        Map<Denomination, Long> m = CashBreakdown.of(1688);
        long sum = 0;
        for (var e : m.entrySet()) sum += e.getKey().value * e.getValue();
        check("breakdown 1688 sum", sum == 1688);
        check("breakdown 1688 has 9 kinds x1", m.size() == 9 && m.values().stream().allMatch(v -> v == 1));
        check("breakdown capped at 100", CashBreakdown.of(1688, Denomination.NOTE_100).get(Denomination.NOTE_100) == 16);
        check("breakdown 0 empty", CashBreakdown.of(0).isEmpty());
        Random r = new Random(1);
        boolean allOk = true;
        for (int i = 0; i < 10000; i++) {
            long x = r.nextInt(5_000_000);
            long s = 0;
            for (var e : CashBreakdown.of(x).entrySet()) s += e.getKey().value * e.getValue();
            if (s != x) allOk = false;
        }
        check("breakdown random sums", allOk);

        BankConfig cfg = new BankConfig();
        Bank bank = new Bank(cfg);
        UUID p1 = UUID.randomUUID(), p2 = UUID.randomUUID();
        bank.deposit(p1, 10_000);
        check("deposit", bank.balance(p1) == 10_000);
        check("deposit 0 rejected", throwsBank(() -> bank.deposit(p1, 0)));
        check("deposit negative rejected", throwsBank(() -> bank.deposit(p1, -5)));

        long fee = bank.withdraw(p1, 1000, 100);
        check("withdraw fee 1%", fee == 10 && bank.balance(p1) == 8990 && bank.balance(Bank.TREASURY) == 10);
        check("withdraw too much", throwsBank(() -> bank.withdraw(p1, 9000, 100)));
        check("daily limit", throwsBank(() -> { bank.deposit(p1, 200_000); bank.withdraw(p1, 49_500, 100); }));
        check("daily limit resets next day", bank.withdraw(p1, 49_500, 101) == 495);

        long tf = bank.transfer(p1, p2, 1000);
        check("transfer receiver gets full", bank.balance(p2) == 1000 && tf == 10);
        check("transfer to self rejected", throwsBank(() -> bank.transfer(p1, p1, 5)));
        check("transfer insufficient", throwsBank(() -> bank.transfer(p2, p1, 1000)));  // ต้อง 1010
        check("treasury sums fees", bank.balance(Bank.TREASURY) == 10 + 495 + 10);

        long before = bank.balance(p1);
        check("overflow rejected", throwsBank(() -> { bank.adminSet(p1, Long.MAX_VALUE / 2000); cfg.maxTransaction = Long.MAX_VALUE; bank.deposit(p1, Long.MAX_VALUE); }));
        bank.adminSet(p1, 500);
        bank.adminTake(p1, 9999);
        check("admin take clamps to 0", bank.balance(p1) == 0);

        // โหลด/บันทึกแล้วต้องเท่าเดิม
        Bank b2 = new Bank(new BankConfig());
        b2.load(bank.snapshotBalances(), bank.snapshotDaily());
        check("snapshot roundtrip", b2.balance(p2) == bank.balance(p2) && b2.withdrawnToday(p1, 101) == bank.withdrawnToday(p1, 101));

        System.out.println(fails == 0 ? "ALL PASSED" : (fails + " FAILED"));
        System.exit(fails == 0 ? 0 : 1);
    }
}
