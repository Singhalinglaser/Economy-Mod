package com.thaimoney.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** ชนิดเงินสดทั้งหมด (ไม่ผูกกับ Minecraft จึงทดสอบแยกได้) */
public enum Denomination {
    COIN_1("coin_1", 1, 64),
    COIN_2("coin_2", 2, 64),
    COIN_5("coin_5", 5, 64),
    COIN_10("coin_10", 10, 64),
    NOTE_20("note_20", 20, 64),
    NOTE_50("note_50", 50, 64),
    NOTE_100("note_100", 100, 16),
    NOTE_500("note_500", 500, 16),
    NOTE_1000("note_1000", 1000, 16);

    public final String id;
    public final long value;
    public final int maxStack;

    Denomination(String id, long value, int maxStack) {
        this.id = id;
        this.value = value;
        this.maxStack = maxStack;
    }

    /** เรียงจากมูลค่าสูงไปต่ำ */
    public static List<Denomination> descending() {
        List<Denomination> list = new ArrayList<>(Arrays.asList(values()));
        list.sort(Comparator.comparingLong((Denomination d) -> d.value).reversed());
        return list;
    }

    public static Denomination byValue(long value) {
        for (Denomination d : values()) if (d.value == value) return d;
        return null;
    }
}
