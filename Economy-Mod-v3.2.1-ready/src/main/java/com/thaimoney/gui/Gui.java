package com.thaimoney.gui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.ItemLike;

/** ตัวช่วยสร้างไอคอนในเมนู */
public final class Gui {
    private Gui() {}

    static Component plain(String text) {
        return Component.literal(text).withStyle(style -> style.withItalic(false));
    }

    public static ItemStack icon(ItemLike item, String name, String... lore) {
        ItemStack s = new ItemStack(item);
        s.set(DataComponents.CUSTOM_NAME, plain(name));
        return withLore(s, lore);
    }

    /** ใส่ lore ให้ไอเทมที่มีชื่อเดิมของมัน (ใช้แสดงสินค้า) */
    public static ItemStack withLore(ItemStack s, String... lore) {
        if (lore.length > 0) {
            List<Component> l = new ArrayList<>();
            for (String x : lore) l.add(plain(x));
            s.set(DataComponents.LORE, new ItemLore(l));
        }
        return s;
    }

    public static ItemStack pane() {
        return icon(Items.GRAY_STAINED_GLASS_PANE, " ");
    }

    public static void msg(ServerPlayer p, String text) {
        p.sendSystemMessage(Component.literal(text));
    }

    public static String pct(int bp) {
        return String.format("%.2f%%", bp / 100.0);
    }
}
