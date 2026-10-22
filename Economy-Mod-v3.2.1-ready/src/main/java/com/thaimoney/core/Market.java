package com.thaimoney.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** กระดานตลาดผู้เล่น: ลงขายราคาคงที่ ผู้ขายได้เงินเข้าบัญชีทันที (แม้ออฟไลน์) หักภาษีเข้ากองกลาง */
public final class Market {
    /** รูปแบบที่บันทึกลงไฟล์ (เก็บเฉพาะ id ไอเทม + จำนวน ไม่เก็บ component จึงไม่มีของปลอม) */
    public static class Listing {
        public long id;
        public String seller;
        public String sellerName;
        public String item;
        public int count;
        public long price;
        public long createdAt;
    }

    private final Map<Long, Listing> listings = new LinkedHashMap<>();
    private long nextId = 1;

    public Listing create(UUID seller, String sellerName, String item, int count, long price,
                          int maxPerPlayer, long now, long maxPrice) throws BankException {
        if (count <= 0) throw new BankException("ไม่มีไอเทมให้ลงขาย");
        if (price <= 0) throw new BankException("ราคาต้องเป็นจำนวนเต็มบวก");
        if (price > maxPrice) throw new BankException("ราคาสูงเกินกำหนด");
        if (bySeller(seller).size() >= maxPerPlayer) {
            throw new BankException("ลงขายได้สูงสุด " + maxPerPlayer + " รายการ");
        }
        Listing l = new Listing();
        l.id = nextId++;
        l.seller = seller.toString();
        l.sellerName = sellerName;
        l.item = item;
        l.count = count;
        l.price = price;
        l.createdAt = now;
        listings.put(l.id, l);
        return l;
    }

    public Listing get(long id) {
        return listings.get(id);
    }

    /** ใหม่สุดก่อน */
    public List<Listing> all() {
        List<Listing> list = new ArrayList<>(listings.values());
        java.util.Collections.reverse(list);
        return list;
    }

    public List<Listing> bySeller(UUID seller) {
        List<Listing> out = new ArrayList<>();
        for (Listing l : listings.values()) if (l.seller.equals(seller.toString())) out.add(l);
        return out;
    }

    /** ซื้อ: โอนเงินก่อน สำเร็จแล้วจึงลบรายการ คืนรายการที่ซื้อได้ */
    public Listing buy(Bank bank, long id, UUID buyer, int taxBp) throws BankException {
        Listing l = listings.get(id);
        if (l == null) throw new BankException("รายการนี้ถูกขายหรือยกเลิกไปแล้ว");
        UUID seller = UUID.fromString(l.seller);
        bank.sale(buyer, seller, l.price, taxBp, "ซื้อ " + l.item + " x" + l.count, "ขาย " + l.item + " x" + l.count);
        listings.remove(id);
        return l;
    }

    /** ยกเลิก: เฉพาะเจ้าของ คืนรายการเพื่อให้ผู้เรียกคืนไอเทม */
    public Listing cancel(long id, UUID requester) throws BankException {
        Listing l = listings.get(id);
        if (l == null) throw new BankException("ไม่พบรายการนี้");
        if (!l.seller.equals(requester.toString())) throw new BankException("ไม่ใช่รายการของคุณ");
        listings.remove(id);
        return l;
    }

    public List<Listing> snapshot() {
        return new ArrayList<>(listings.values());
    }

    public long nextId() {
        return nextId;
    }

    public void load(List<Listing> in, long next) {
        listings.clear();
        long max = 0;
        for (Listing l : in) {
            listings.put(l.id, l);
            max = Math.max(max, l.id);
        }
        nextId = Math.max(next, max + 1);
    }
}
