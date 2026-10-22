package com.thaimoney.core;

import java.util.LinkedHashMap;
import java.util.Map;

/** แตกยอดเงินเป็นแบงก์/เหรียญ (greedy ใช้ได้เพราะชุด 1,2,5,10,20,50,100,500,1000 เป็น canonical) */
public final class CashBreakdown {
    private CashBreakdown() {}

    public static Map<Denomination, Long> of(long amount) {
        return of(amount, Denomination.NOTE_1000);
    }

    /** แตกยอดโดยไม่ใช้ชนิดที่ใหญ่กว่า largestAllowed */
    public static Map<Denomination, Long> of(long amount, Denomination largestAllowed) {
        if (amount < 0) throw new IllegalArgumentException("amount < 0");
        Map<Denomination, Long> result = new LinkedHashMap<>();
        long rest = amount;
        for (Denomination d : Denomination.descending()) {
            if (d.value > largestAllowed.value) continue;
            long n = rest / d.value;
            if (n > 0) {
                result.put(d, n);
                rest -= n * d.value;
            }
        }
        return result;
    }
}
