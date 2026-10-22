package com.thaimoney.gui;

import com.thaimoney.CashUtil;
import com.thaimoney.ModItems;
import com.thaimoney.MoneyMod;
import com.thaimoney.core.Denomination;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Items;

/** กระเป๋าสตางค์: เก็บเงินสดแยกชนิดนอกช่องเก็บของ (ต้องถือ/พกกระเป๋าสตางค์ไว้ในตัวถึงจะเปิดได้) */
public final class WalletMenu extends GuiMenu {
    private final UUID me;

    public static void open(ServerPlayer p) {
        if (!CashUtil.hasWallet(p.getInventory())) {
            Gui.msg(p, "§cต้องมีกระเป๋าสตางค์ติดตัว");
            return;
        }
        p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new WalletMenu(id, inv, (ServerPlayer) pl),
                Component.literal("กระเป๋าสตางค์")));
    }

    private WalletMenu(int syncId, Inventory inv, ServerPlayer p) {
        super(syncId, inv, 3);
        this.me = p.getUUID();
        render();
    }

    private void render() {
        clearAll();
        fillPanes();
        var wallets = MoneyMod.wallets();
        set(4, Gui.icon(Items.GOLD_NUGGET, "รวมในกระเป๋า: ฿" + wallets.total(me)));
        Denomination[] all = Denomination.values();
        for (int i = 0; i < all.length; i++) {
            final Denomination d = all[i];
            long have = wallets.count(me, d);
            button(9 + i, Gui.icon(ModItems.cash(d), "฿" + d.value + " × " + have,
                    "§7ซ้าย: เอาออก 1 ชิ้น | Shift+ซ้าย: เอาออก 1 สแตก",
                    "§7ขวา: ใส่เข้า 1 ชิ้น | Shift+ขวา: ใส่เข้าทั้งหมด"), (p, t, b) -> click(p, d, t, b));
        }
        button(22, Gui.icon(Items.HOPPER, "ใส่เงินสดทั้งหมดในตัวเข้ากระเป๋า"), (p, t, b) -> {
            if (!CashUtil.hasWallet(p.getInventory())) {
                p.closeContainer();
                return;
            }
            for (Denomination d : Denomination.values()) {
                long n = CashUtil.removeDenom(p.getInventory(), d, Long.MAX_VALUE);
                wallets.add(me, d, n);
            }
            render();
        });
        button(26, Gui.icon(Items.BARRIER, "ปิด"), (p, t, b) -> p.closeContainer());
    }

    private void click(ServerPlayer p, Denomination d, ClickType type, int button) {
        if (!CashUtil.hasWallet(p.getInventory())) {
            p.closeContainer();
            return;
        }
        var wallets = MoneyMod.wallets();
        boolean shift = type == ClickType.QUICK_MOVE;
        if (button == 0) { // เอาออก
            long n = Math.min(shift ? d.maxStack : 1, wallets.count(me, d));
            if (n > 0 && wallets.take(me, d, n)) CashUtil.giveExact(p, d, n);
        } else { // ใส่เข้า
            long have = CashUtil.countDenom(p.getInventory(), d);
            long n = shift ? have : Math.min(1, have);
            if (n > 0) wallets.add(me, d, CashUtil.removeDenom(p.getInventory(), d, n));
        }
        render();
    }
}
