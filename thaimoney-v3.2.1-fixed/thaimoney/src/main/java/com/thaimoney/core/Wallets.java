package com.thaimoney.core;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** กระเป๋าสตางค์: บันทึกจำนวนเงินสดแต่ละชนิดของผู้เล่น (ฝั่งเซิร์ฟเวอร์ ปลอม/dupe ไม่ได้) */
public final class Wallets {
    private final Map<UUID, Map<Denomination, Long>> data = new HashMap<>();

    public long count(UUID id, Denomination d) {
        return data.getOrDefault(id, Map.of()).getOrDefault(d, 0L);
    }

    public long total(UUID id) {
        long t = 0;
        for (var e : data.getOrDefault(id, Map.of()).entrySet()) t += e.getKey().value * e.getValue();
        return t;
    }

    public void add(UUID id, Denomination d, long n) {
        if (n <= 0) return;
        data.computeIfAbsent(id, k -> new EnumMap<>(Denomination.class)).merge(d, n, Math::addExact);
    }

    /** เอาออก n ชิ้น สำเร็จเมื่อมีพอเท่านั้น */
    public boolean take(UUID id, Denomination d, long n) {
        if (n <= 0 || count(id, d) < n) return false;
        data.get(id).merge(d, -n, Long::sum);
        return true;
    }

    public void clear(UUID id) {
        data.remove(id);
    }

    public Map<UUID, Map<String, Long>> snapshot() {
        Map<UUID, Map<String, Long>> out = new HashMap<>();
        data.forEach((id, m) -> {
            Map<String, Long> inner = new HashMap<>();
            m.forEach((d, n) -> {
                if (n > 0) inner.put(d.id, n);
            });
            if (!inner.isEmpty()) out.put(id, inner);
        });
        return out;
    }

    public void load(Map<UUID, Map<String, Long>> in) {
        data.clear();
        in.forEach((id, m) -> m.forEach((key, n) -> {
            for (Denomination d : Denomination.values()) if (d.id.equals(key)) add(id, d, n);
        }));
    }
}
