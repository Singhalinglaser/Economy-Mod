package com.thaimoney.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** กล่องรับของ: ไอเทมที่ส่งมอบให้ผู้เล่นที่ออฟไลน์ (เช่น ชนะประมูล) รับด้วย /claim */
public final class Mailbox {
    public static class Entry {
        public String item;
        public int count;

        public Entry() {}

        public Entry(String item, int count) {
            this.item = item;
            this.count = count;
        }
    }

    private final Map<UUID, List<Entry>> boxes = new HashMap<>();

    public void add(UUID id, String item, int count) {
        if (count <= 0) return;
        boxes.computeIfAbsent(id, k -> new ArrayList<>()).add(new Entry(item, count));
    }

    public int pending(UUID id) {
        int n = 0;
        for (Entry e : boxes.getOrDefault(id, List.of())) n += e.count;
        return n;
    }

    /** เอาของทั้งหมดออกจากกล่อง */
    public List<Entry> drain(UUID id) {
        List<Entry> out = boxes.remove(id);
        return out == null ? List.of() : out;
    }

    public Map<UUID, List<Entry>> snapshot() {
        Map<UUID, List<Entry>> copy = new HashMap<>();
        boxes.forEach((k, v) -> copy.put(k, new ArrayList<>(v)));
        return copy;
    }

    public void load(Map<UUID, List<Entry>> in) {
        boxes.clear();
        in.forEach((k, v) -> boxes.put(k, new ArrayList<>(v)));
    }
}
