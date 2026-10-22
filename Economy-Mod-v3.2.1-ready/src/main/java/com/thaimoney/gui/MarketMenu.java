package com.thaimoney.gui;

import com.thaimoney.CashUtil;
import com.thaimoney.MoneyMod;
import com.thaimoney.ShopCatalog;
import com.thaimoney.core.BankException;
import com.thaimoney.core.Market;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** กระดานตลาดผู้เล่น: ซ้ายคลิกซื้อ ขวาคลิกยกเลิก (เฉพาะของตัวเอง) */
public final class MarketMenu extends GuiMenu {
    private static final int PER_PAGE = 45;
    private final UUID me;
    private int page;

    public static void open(ServerPlayer p) {
        p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new MarketMenu(id, inv, (ServerPlayer) pl),
                Component.literal("ตลาดผู้เล่น")));
    }

    private MarketMenu(int syncId, Inventory inv, ServerPlayer p) {
        super(syncId, inv, 6);
        this.me = p.getUUID();
        render();
    }

    private void render() {
        clearAll();
        fillPanes();
        List<Market.Listing> all = MoneyMod.market().all();
        int pages = Math.max(1, (all.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.max(0, Math.min(page, pages - 1));
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = page * PER_PAGE + i;
            if (idx >= all.size()) {
                set(i, ItemStack.EMPTY);
                continue;
            }
            Market.Listing l = all.get(idx);
            Item item = ShopCatalog.itemById(l.item);
            if (item == null) {
                set(i, ItemStack.EMPTY);
                continue;
            }
            boolean mine = l.seller.equals(me.toString());
            ItemStack shown = Gui.withLore(new ItemStack(item, l.count),
                    "§aราคา: ฿" + l.price,
                    "§7ผู้ขาย: " + l.sellerName,
                    mine ? "§eของคุณ — คลิกขวาเพื่อยกเลิกและรับของคืน"
                            : "§7คลิกซ้ายเพื่อซื้อ (ผู้ขายเสียภาษี " + Gui.pct(MoneyMod.taxFor(UUID.fromString(l.seller))) + ")");
            final long id = l.id;
            button(i, shown, (p, t, b) -> {
                if (b == 0 && !mine) buy(p, id);
                else if (b == 1 && mine) cancel(p, id);
                render();
            });
        }
        set(49, Gui.icon(Items.GOLD_INGOT, "ยอดคงเหลือ: ฿" + MoneyMod.bank().balance(me),
                "§7หน้า " + (page + 1) + "/" + pages, "§7ลงขาย: ถือของแล้วพิมพ์ /market sell <ราคา>"));
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

    private void buy(ServerPlayer p, long id) {
        try {
            Market.Listing pending = MoneyMod.market().get(id);
            int tax = pending == null ? 0 : MoneyMod.taxFor(UUID.fromString(pending.seller));
            Market.Listing l = MoneyMod.market().buy(MoneyMod.bank(), id, me, tax);
            Item item = ShopCatalog.itemById(l.item);
            if (item != null) CashUtil.giveItem(p, item, l.count);
            Gui.msg(p, "§a[ตลาด] ซื้อ " + l.item + " x" + l.count + " ราคา ฿" + l.price);
            ServerPlayer seller = MoneyMod.server().getPlayerList().getPlayer(UUID.fromString(l.seller));
            if (seller != null) Gui.msg(seller, "§a[ตลาด] ขาย " + l.item + " x" + l.count + " ได้แล้ว (฿" + l.price + " ก่อนหักภาษี)");
        } catch (BankException e) {
            Gui.msg(p, "§c[ตลาด] " + e.getMessage());
        }
    }

    private void cancel(ServerPlayer p, long id) {
        try {
            Market.Listing l = MoneyMod.market().cancel(id, me);
            Item item = ShopCatalog.itemById(l.item);
            if (item != null) CashUtil.giveItem(p, item, l.count);
            Gui.msg(p, "§e[ตลาด] ยกเลิกรายการและรับของคืนแล้ว");
        } catch (BankException e) {
            Gui.msg(p, "§c[ตลาด] " + e.getMessage());
        }
    }
}
