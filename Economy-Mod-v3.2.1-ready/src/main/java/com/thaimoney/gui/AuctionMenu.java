package com.thaimoney.gui;

import com.thaimoney.MoneyMod;
import com.thaimoney.ShopCatalog;
import com.thaimoney.core.Auctions;
import com.thaimoney.core.BankException;
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

/** กระดานประมูล: ซ้าย = เสนอราคาขั้นต่ำ, Shift+ซ้าย = ขั้นต่ำ +10%, ขวา = ยกเลิก (ของตัวเอง ถ้ายังไม่มีผู้เสนอ) */
public final class AuctionMenu extends GuiMenu {
    private static final int PER_PAGE = 45;
    private final UUID me;
    private int page;

    public static void open(ServerPlayer p) {
        p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new AuctionMenu(id, inv, (ServerPlayer) pl),
                Component.literal("ประมูล")));
    }

    private AuctionMenu(int syncId, Inventory inv, ServerPlayer p) {
        super(syncId, inv, 6);
        this.me = p.getUUID();
        render();
    }

    public static String timeLeft(long ms) {
        long s = Math.max(0, ms / 1000);
        if (s >= 3600) return (s / 3600) + " ชม. " + ((s % 3600) / 60) + " นาที";
        return (s / 60) + " นาที " + (s % 60) + " วินาที";
    }

    private void render() {
        clearAll();
        fillPanes();
        long now = System.currentTimeMillis();
        List<Auctions.Auction> all = MoneyMod.auctions().all();
        int pages = Math.max(1, (all.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.max(0, Math.min(page, pages - 1));
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = page * PER_PAGE + i;
            if (idx >= all.size()) {
                set(i, ItemStack.EMPTY);
                continue;
            }
            Auctions.Auction a = all.get(idx);
            Item item = ShopCatalog.itemById(a.item);
            if (item == null) {
                set(i, ItemStack.EMPTY);
                continue;
            }
            boolean mine = a.seller.equals(me.toString());
            long min = Auctions.minNextBid(a);
            ItemStack shown = Gui.withLore(new ItemStack(item, a.count),
                    a.bidder == null ? "§aราคาเริ่มต้น: ฿" + a.startPrice
                            : "§aราคาปัจจุบัน: ฿" + a.currentBid + " §7(" + a.bidderName + ")",
                    "§eเสนอขั้นต่ำ: ฿" + min,
                    "§7เหลือเวลา: " + timeLeft(a.endsAt - now),
                    "§7ผู้ขาย: " + a.sellerName + " | รหัส #" + a.id,
                    mine ? (a.bidder == null ? "§eของคุณ — คลิกขวาเพื่อยกเลิก" : "§eของคุณ — มีผู้เสนอราคาแล้ว")
                            : "§7ซ้าย: เสนอขั้นต่ำ | Shift+ซ้าย: ขั้นต่ำ +10%");
            final long id = a.id;
            button(i, shown, (p, t, b) -> {
                if (b == 0) bid(p, id, t == ClickType.QUICK_MOVE);
                else if (b == 1 && mine) cancel(p, id);
                render();
            });
        }
        set(49, Gui.icon(Items.GOLD_INGOT, "ยอดคงเหลือ: ฿" + MoneyMod.bank().balance(me),
                "§7หน้า " + (page + 1) + "/" + pages,
                "§7เปิดประมูล: ถือของแล้ว /auction start <ราคาเริ่ม> <นาที>",
                "§7ค่าธรรมเนียมผู้ขาย " + Gui.pct(MoneyMod.bank().config().auctionFeeBp)));
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

    private void bid(ServerPlayer p, long id, boolean extra) {
        Auctions.Auction a = MoneyMod.auctions().get(id);
        if (a == null) {
            Gui.msg(p, "§c[ประมูล] ไม่พบการประมูลนี้");
            return;
        }
        long amount = Auctions.minNextBid(a);
        if (extra) amount += Math.max(10, a.currentBid / 10);
        try {
            MoneyMod.auctions().bid(MoneyMod.bank(), id, me, p.getName().getString(), amount, System.currentTimeMillis());
            Gui.msg(p, "§a[ประมูล] เสนอราคา ฿" + amount + " สำเร็จ (กันเงินไว้ คืนอัตโนมัติถ้าถูกเสนอสูงกว่า)");
        } catch (BankException e) {
            Gui.msg(p, "§c[ประมูล] " + e.getMessage());
        }
    }

    private void cancel(ServerPlayer p, long id) {
        try {
            MoneyMod.auctions().cancel(id, me, MoneyMod.mailbox());
            Gui.msg(p, "§e[ประมูล] ยกเลิกแล้ว รับของคืนด้วย /claim");
        } catch (BankException e) {
            Gui.msg(p, "§c[ประมูล] " + e.getMessage());
        }
    }

}
