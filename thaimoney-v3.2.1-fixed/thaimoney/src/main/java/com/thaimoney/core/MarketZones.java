package com.thaimoney.core;

import java.util.ArrayList;
import java.util.List;

/** โซนตลาดที่แอดมินกำหนด (กล่อง 3 มิติในมิติใดมิติหนึ่ง) ถ้ายังไม่มีโซนเลย = ไม่จำกัดพื้นที่ */
public final class MarketZones {
    public static class Zone {
        public String dimension;
        public int minX, minY, minZ, maxX, maxY, maxZ;
    }

    private final List<Zone> zones = new ArrayList<>();

    public Zone add(String dimension, int x1, int y1, int z1, int x2, int y2, int z2) {
        Zone z = new Zone();
        z.dimension = dimension;
        z.minX = Math.min(x1, x2);
        z.maxX = Math.max(x1, x2);
        z.minY = Math.min(y1, y2);
        z.maxY = Math.max(y1, y2);
        z.minZ = Math.min(z1, z2);
        z.maxZ = Math.max(z1, z2);
        zones.add(z);
        return z;
    }

    /** ลบโซนตามลำดับ (เริ่มที่ 1) คืน true ถ้าลบได้ */
    public boolean remove(int index1) {
        if (index1 < 1 || index1 > zones.size()) return false;
        zones.remove(index1 - 1);
        return true;
    }

    public boolean isEmpty() {
        return zones.isEmpty();
    }

    public boolean contains(String dimension, int x, int y, int z) {
        for (Zone zn : zones) {
            if (zn.dimension.equals(dimension) && x >= zn.minX && x <= zn.maxX && y >= zn.minY && y <= zn.maxY
                    && z >= zn.minZ && z <= zn.maxZ) {
                return true;
            }
        }
        return false;
    }

    /** ไม่มีโซนเลย = ทุกที่อนุญาต */
    public boolean allows(String dimension, int x, int y, int z) {
        return zones.isEmpty() || contains(dimension, x, y, z);
    }

    public List<Zone> snapshot() {
        return new ArrayList<>(zones);
    }

    public void load(List<Zone> in) {
        zones.clear();
        zones.addAll(in);
    }
}
