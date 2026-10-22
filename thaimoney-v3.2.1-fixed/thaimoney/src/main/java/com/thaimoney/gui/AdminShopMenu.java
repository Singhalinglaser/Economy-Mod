package com.thaimoney.gui;

import com.thaimoney.CashUtil;
import com.thaimoney.MoneyMod;
import com.thaimoney.ShopCatalog;
import com.thaimoney.core.BankException;
import com.thaimoney.core.ShopPricing;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** ร้านระบบ: ราคาขึ้นลงตามอุปสงค์อุปทาน จ่าย/รับผ่านบัญชีธนาคาร */
public final class AdminShopMenu extends GuiMenu {
    private static final int PER_PAGE = 45;
    private final UUID me;
    private int page;

    public static void open(ServerPlayer p) {
        p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new AdminShopMenu(id, inv, (ServerPlayer) pl),
                Component.literal("ร้านระบบ")));
    }

    private AdminShopMenu(int syncId, Inventory inv, ServerPlayer p) {
        super(syncId, inv, 6);
        this.me = p.getUUID();
        render();
    }

    private void render() {
        clearAll();
        fillPanes();
        ShopPricing pricing = MoneyMod.pricing();
        long now = System.currentTimeMillis();
        List<ShopPricing.Entry> list = ShopCatalog.entries();
        int pages = Math.max(1, (list.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.max(0, Math.min(page, pages - 1));
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = page * PER_PAGE + i;
            if (idx >= list.size()) {
                set(i, ItemStack.EMPTY);
                continue;
            }
            ShopPricing.Entry e = list.get(idx);
            Item item = ShopCatalog.itemById(e.item);
            if (item == null) {
                set(i, ItemStack.EMPTY);
                continue;
            }
            ItemStack shown = Gui.withLore(new ItemStack(item),
                    "§aซื้อ: ฿" + pricing.unitBuyNow(e, now) + " §7| §eขายให้ร้าน: ฿" + pricing.unitSellNow(e, now),
                    "§7ซ้าย: ซื้อ 1 | Shift+ซ้าย: ซื้อ 16",
                    "§7ขวา: ขาย 1 | Shift+ขวา: ขาย 16",
                    "§8รับซื้อเฉพาะไอเทมธรรมดา (ไม่มีชื่อ/เอนแชนต์/ความทนทานลด)");
            button(i, shown, (p, t, b) -> {
                boolean shift = t == ClickType.QUICK_MOVE;
                int n = shift ? 16 : 1;
                if (b == 0) buy(p, e, item, n);
                else sell(p, e, item, n);
                render();
            });
        }
        set(49, Gui.icon(Items.GOLD_INGOT, "ยอดคงเหลือ: ฿" + MoneyMod.bank().balance(me), "§7หน้า " + (page + 1) + "/" + pages));
        if (page > 0) {
            button(45, Gui.icon(Items.ARROW, "หน้าก่อน"), (p, t, b) -> {
                page--;
                render();
            });
        }
        if (page < pages - 1) {
            button(53, Gui.icon(Items.ARROW, "หน้าถัดไป"), (p, t, b) -> {
                page++;
                render();
            });
        }
    }

    private void buy(ServerPlayer p, ShopPricing.Entry e, Item item, int n) {
        long now = System.currentTimeMillis();
        long cost = MoneyMod.pricing().quoteBuy(e, n, now);
        try {
            MoneyMod.bank().spend(me, cost, "ซื้อ " + e.item + " x" + n);
            MoneyMod.pricing().applyBuy(e, n, now);
            CashUtil.giveItem(p, item, n);
            Gui.msg(p, "§a[ร้านระบบ] ซื้อ " + e.item + " x" + n + " ราคา ฿" + cost);
        } catch (BankException ex) {
            Gui.msg(p, "§c[ร้านระบบ] " + ex.getMessage());
        }
    }

    private void sell(ServerPlayer p, ShopPricing.Entry e, Item item, int n) {
        int have = CashUtil.countPlain(p.getInventory(), item);
        int count = Math.min(n, have);
        if (count <= 0) {
            Gui.msg(p, "§c[ร้านระบบ] คุณไม่มี " + e.item + " ธรรมดาในตัว");
            return;
        }
        long now = System.currentTimeMillis();
        long gain = MoneyMod.pricing().quoteSell(e, count, now);
        try {
            MoneyMod.bank().earn(me, gain, "ขาย " + e.item + " x" + count);
            CashUtil.removePlain(p.getInventory(), item, count);
            MoneyMod.pricing().applySell(e, count, now);
            Gui.msg(p, "§a[ร้านระบบ] ขาย " + e.item + " x" + count + " ได้ ฿" + gain);
        } catch (BankException ex) {
            Gui.msg(p, "§c[ร้านระบบ] " + ex.getMessage());
        }
    }
}
