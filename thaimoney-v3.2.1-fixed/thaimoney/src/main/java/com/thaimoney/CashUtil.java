package com.thaimoney;

import com.thaimoney.core.CashBreakdown;
import com.thaimoney.core.Denomination;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** ฟังก์ชันช่วยจัดการเงินสดและไอเทมในช่องเก็บของผู้เล่น */
public final class CashUtil {
    private CashUtil() {}

    public static boolean hasWallet(Inventory inv) {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(ModItems.WALLET)) return true;
        }
        return false;
    }

    public static long countCash(Inventory inv) {
        long total = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.getItem() instanceof CashItem c) total += c.denomination().value * s.getCount();
        }
        return total;
    }

    /** เอาเงินสดทั้งหมดออกจากช่องเก็บของ คืนมูลค่ารวม */
    public static long takeAllCash(Inventory inv) {
        long total = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.getItem() instanceof CashItem c) {
                total += c.denomination().value * s.getCount();
                inv.setItem(i, ItemStack.EMPTY);
            }
        }
        return total;
    }

    public static long countDenom(Inventory inv, Denomination d) {
        long n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.getItem() instanceof CashItem c && c.denomination() == d) n += s.getCount();
        }
        return n;
    }

    /** เอาเงินชนิดนี้ออกไม่เกิน max ชิ้น คืนจำนวนที่เอาออกจริง */
    public static long removeDenom(Inventory inv, Denomination d, long max) {
        long removed = 0;
        for (int i = 0; i < inv.getContainerSize() && removed < max; i++) {
            ItemStack s = inv.getItem(i);
            if (s.getItem() instanceof CashItem c && c.denomination() == d) {
                int take = (int) Math.min(s.getCount(), max - removed);
                if (take >= s.getCount()) inv.setItem(i, ItemStack.EMPTY);
                else s.shrink(take);
                removed += take;
            }
        }
        return removed;
    }

    /** ให้เงินสดตามยอด (แตกเป็นแบงก์อัตโนมัติ) ถ้าช่องเก็บของเต็มจะดรอปที่เท้า */
    public static void give(ServerPlayer player, long amount, Denomination largest) {
        Map<Denomination, Long> parts = CashBreakdown.of(amount, largest);
        for (Map.Entry<Denomination, Long> e : parts.entrySet()) giveExact(player, e.getKey(), e.getValue());
    }

    public static void giveExact(ServerPlayer player, Denomination d, long n) {
        giveItem(player, ModItems.cash(d), n);
    }

    public static void giveItem(ServerPlayer player, Item item, long n) {
        long left = n;
        while (left > 0) {
            int stackSize = Math.max(1, new ItemStack(item).getMaxStackSize());
            int c = (int) Math.min(left, stackSize);
            ItemStack stack = new ItemStack(item, c);
            if (!player.getInventory().add(stack)) player.drop(stack, false);
            left -= c;
        }
    }

    /** นับไอเทมธรรมดา (ไม่มีชื่อ/เอนแชนต์/ความทนทานที่เปลี่ยน) ชนิดนี้ในช่องเก็บของ */
    public static int countPlain(Inventory inv, Item item) {
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(item) && s.getComponentsPatch().isEmpty()) n += s.getCount();
        }
        return n;
    }

    public static int removePlain(Inventory inv, Item item, int max) {
        int removed = 0;
        for (int i = 0; i < inv.getContainerSize() && removed < max; i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(item) && s.getComponentsPatch().isEmpty()) {
                int take = Math.min(s.getCount(), max - removed);
                if (take >= s.getCount()) inv.setItem(i, ItemStack.EMPTY);
                else s.shrink(take);
                removed += take;
            }
        }
        return removed;
    }
}
