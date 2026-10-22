import com.thaimoney.core.*;

public class CoreTest4 {
    static int fails = 0;
    static void check(String n, boolean ok) { System.out.println((ok ? "PASS " : "FAIL ") + n); if (!ok) fails++; }

    public static void main(String[] a) {
        MarketZones z = new MarketZones();
        check("no zones → allowed anywhere", z.allows("overworld", 0, 0, 0) && z.isEmpty());
        z.add("overworld", 10, 70, 10, -10, 60, -10);   // มุมสลับลำดับ ต้อง normalize
        check("inside zone", z.allows("overworld", 0, 65, 0));
        check("corner inclusive", z.allows("overworld", 10, 70, 10) && z.allows("overworld", -10, 60, -10));
        check("outside x", !z.allows("overworld", 11, 65, 0));
        check("outside y", !z.allows("overworld", 0, 71, 0));
        check("other dimension denied", !z.allows("nether", 0, 65, 0));
        z.add("nether", 0, 0, 0, 5, 5, 5);
        check("second zone works", z.allows("nether", 3, 3, 3));
        MarketZones z2 = new MarketZones();
        z2.load(z.snapshot());
        check("zones roundtrip", z2.allows("nether", 3, 3, 3) && !z2.allows("nether", 9, 9, 9));
        check("remove bad index false", !z.remove(0) && !z.remove(3));
        check("remove ok", z.remove(2) && !z.allows("nether", 3, 3, 3));
        z.remove(1);
        check("all removed → allowed anywhere again", z.allows("overworld", 999, 1, 999));
        System.out.println(fails == 0 ? "ALL PASSED" : (fails + " FAILED"));
        System.exit(fails == 0 ? 0 : 1);
    }
}
