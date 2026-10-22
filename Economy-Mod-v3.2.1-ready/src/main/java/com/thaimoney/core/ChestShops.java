package com.thaimoney.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** ตู้ขายของ: หีบที่เจ้าของลงทะเบียน ขายไอเทมชนิดเดียวราคาต่อชิ้น ผู้ซื้อคลิกขวาที่หีบเพื่อซื้อ */
public final class ChestShops {
    public static class Shop {
        public String key;
        public String owner;
        public String ownerName;
        public String item;
        public long price;
    }

    private final Map<String, Shop> shops = new HashMap<>();

    public static String key(String dimension, int x, int y, int z) {
        return dimension + "|" + x + "|" + y + "|" + z;
    }

    public Shop create(UUID owner, String ownerName, String key, String item, long price,
                       int maxPerOwner, long maxPrice) throws BankException {
        if (price <= 0) throw new BankException("ราคาต้องเป็นจำนวนเต็มบวก");
        if (price > maxPrice) throw new BankException("ราคาสูงเกินกำหนด");
        if (shops.containsKey(key)) throw new BankException("หีบนี้เป็นตู้ขายของอยู่แล้ว");
        if (byOwner(owner).size() >= maxPerOwner) throw new BankException("เปิดตู้ขายของได้สูงสุด " + maxPerOwner + " ตู้");
        Shop s = new Shop();
        s.key = key;
        s.owner = owner.toString();
        s.ownerName = ownerName;
        s.item = item;
        s.price = price;
        shops.put(key, s);
        return s;
    }

    public Shop get(String key) {
        return shops.get(key);
    }

    public Shop remove(String key) {
        return shops.remove(key);
    }

    public List<Shop> byOwner(UUID owner) {
        List<Shop> out = new ArrayList<>();
        for (Shop s : shops.values()) if (s.owner.equals(owner.toString())) out.add(s);
        return out;
    }

    public List<Shop> snapshot() {
        return new ArrayList<>(shops.values());
    }

    public void load(List<Shop> in) {
        shops.clear();
        for (Shop s : in) shops.put(s.key, s);
    }
}
