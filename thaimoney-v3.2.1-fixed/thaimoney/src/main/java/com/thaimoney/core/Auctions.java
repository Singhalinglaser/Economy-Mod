package com.thaimoney.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * กระดานประมูล: เงินของผู้เสนอราคาถูกกันไว้ทันที (หักจากบัญชี) และคืนเมื่อถูกเสนอราคาสูงกว่า
 * เมื่อจบ: ผู้ขายได้ราคาหักค่าธรรมเนียม ค่าธรรมเนียมเข้ากองกลาง ไอเทมส่งเข้า Mailbox ของผู้ชนะ (หรือคืนผู้ขายถ้าไม่มีใครประมูล)
 * เสนอราคาในช่วง 2 นาทีสุดท้ายจะต่อเวลาให้เหลือ 2 นาที (กันดักประมูลวินาทีสุดท้าย)
 */
public final class Auctions {
    public static class Auction {
        public long id;
        public String seller;
        public String sellerName;
        public String item;
        public int count;
        public long startPrice;
        public long currentBid;
        public String bidder;
        public String bidderName;
        public long endsAt;
    }

    public record Settlement(Auction auction, boolean sold, long fee) {}

    public static final long EXTEND_WINDOW_MS = 120_000L;

    private final Map<Long, Auction> map = new LinkedHashMap<>();
    private long nextId = 1;

    public Auction create(UUID seller, String sellerName, String item, int count, long startPrice,
                          long durationMs, long now, int maxPerPlayer, long maxPrice) throws BankException {
        if (count <= 0) throw new BankException("ไม่มีไอเทมให้ประมูล");
        if (startPrice <= 0) throw new BankException("ราคาเริ่มต้นต้องเป็นจำนวนเต็มบวก");
        if (startPrice > maxPrice) throw new BankException("ราคาสูงเกินกำหนด");
        if (durationMs < 60_000L) throw new BankException("ระยะเวลาประมูลอย่างน้อย 1 นาที");
        if (bySeller(seller).size() >= maxPerPlayer) throw new BankException("เปิดประมูลพร้อมกันได้สูงสุด " + maxPerPlayer + " รายการ");
        Auction a = new Auction();
        a.id = nextId++;
        a.seller = seller.toString();
        a.sellerName = sellerName;
        a.item = item;
        a.count = count;
        a.startPrice = startPrice;
        a.endsAt = now + durationMs;
        map.put(a.id, a);
        return a;
    }

    public Auction get(long id) {
        return map.get(id);
    }

    public List<Auction> all() {
        return new ArrayList<>(map.values());
    }

    public List<Auction> bySeller(UUID seller) {
        List<Auction> out = new ArrayList<>();
        for (Auction a : map.values()) if (a.seller.equals(seller.toString())) out.add(a);
        return out;
    }

    /** ราคาขั้นต่ำที่เสนอได้ตอนนี้ (ต้องสูงกว่าราคาปัจจุบันอย่างน้อย 5%) */
    public static long minNextBid(Auction a) {
        return a.bidder == null ? a.startPrice : a.currentBid + Math.max(1, a.currentBid / 20);
    }

    public Auction bid(Bank bank, long id, UUID bidder, String bidderName, long amount, long now) throws BankException {
        Auction a = map.get(id);
        if (a == null) throw new BankException("ไม่พบการประมูลนี้");
        if (now >= a.endsAt) throw new BankException("การประมูลนี้จบแล้ว");
        if (a.seller.equals(bidder.toString())) throw new BankException("ประมูลของตัวเองไม่ได้");
        long min = minNextBid(a);
        if (amount < min) throw new BankException("ต้องเสนออย่างน้อย ฿" + min);
        boolean same = a.bidder != null && a.bidder.equals(bidder.toString());
        // หักเงินผู้เสนอรายใหม่ก่อน (ถ้าไม่พอจะ throw และไม่มีอะไรเปลี่ยน) แล้วค่อยคืนเงินรายเก่า
        bank.spend(bidder, same ? amount - a.currentBid : amount, "ประมูล #" + id + " (กันเงินไว้)");
        if (a.bidder != null && !same) {
            bank.earn(UUID.fromString(a.bidder), a.currentBid, "ประมูล #" + id + " (คืนเงิน ถูกเสนอสูงกว่า)");
        }
        a.bidder = bidder.toString();
        a.bidderName = bidderName;
        a.currentBid = amount;
        if (a.endsAt - now < EXTEND_WINDOW_MS) a.endsAt = now + EXTEND_WINDOW_MS;
        return a;
    }

    /** ยกเลิกได้เฉพาะเจ้าของ และเฉพาะตอนยังไม่มีผู้เสนอราคา ไอเทมคืนทาง Mailbox */
    public Auction cancel(long id, UUID requester, Mailbox mail) throws BankException {
        Auction a = map.get(id);
        if (a == null) throw new BankException("ไม่พบการประมูลนี้");
        if (!a.seller.equals(requester.toString())) throw new BankException("ไม่ใช่การประมูลของคุณ");
        if (a.bidder != null) throw new BankException("มีผู้เสนอราคาแล้ว ยกเลิกไม่ได้");
        map.remove(id);
        mail.add(requester, a.item, a.count);
        return a;
    }

    /** ปิดประมูลที่หมดเวลา */
    public List<Settlement> settleDue(Bank bank, Mailbox mail, long now, int feeBp) {
        List<Settlement> out = new ArrayList<>();
        for (Auction a : new ArrayList<>(map.values())) {
            if (a.endsAt > now) continue;
            map.remove(a.id);
            UUID seller = UUID.fromString(a.seller);
            if (a.bidder == null) {
                mail.add(seller, a.item, a.count);
                out.add(new Settlement(a, false, 0));
                continue;
            }
            long fee = Bank.fee(a.currentBid, feeBp);
            try {
                if (a.currentBid - fee > 0) bank.earn(seller, a.currentBid - fee, "ขายประมูล #" + a.id + " (ค่าธรรมเนียม " + fee + ")");
                if (fee > 0) bank.earn(Bank.TREASURY, fee, "ค่าธรรมเนียมประมูล");
            } catch (BankException e) {
                throw new IllegalStateException(e);
            }
            mail.add(UUID.fromString(a.bidder), a.item, a.count);
            out.add(new Settlement(a, true, fee));
        }
        return out;
    }

    public List<Auction> snapshot() {
        return new ArrayList<>(map.values());
    }

    public long nextId() {
        return nextId;
    }

    public void load(List<Auction> in, long next) {
        map.clear();
        long max = 0;
        for (Auction a : in) {
            map.put(a.id, a);
            max = Math.max(max, a.id);
        }
        nextId = Math.max(next, max + 1);
    }
}
