package com.thaimoney;

import com.thaimoney.core.BankException;
import com.thaimoney.core.ChestShops;
import com.thaimoney.gui.Gui;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;

/**
 * ตู้ขายของ: ผู้ซื้อคลิกขวาที่หีบเพื่อซื้อ (Shift = 16 ชิ้น) เจ้าของคลิกขวาเพื่อเปิดหีบเติมของตามปกติ
 * ใช้กับหีบเดี่ยว (หีบคู่ลงทะเบียนได้แค่ฝั่งที่เล็ง และอ่านสต็อกเฉพาะฝั่งนั้น)
 */
public final class ChestShopHandler {
    private ChestShopHandler() {}

    public static String keyOf(Level level, BlockPos pos) {
        return ChestShops.key(level.dimension().toString(), pos.getX(), pos.getY(), pos.getZ());
    }

    public static InteractionResult onUse(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || level.isClientSide()) return InteractionResult.PASS;
        BlockPos pos = hit.getBlockPos();
        ChestShops.Shop shop = MoneyMod.chestShops().get(keyOf(level, pos));
        if (shop == null || !(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        UUID owner = UUID.fromString(shop.owner);
        if (owner.equals(sp.getUUID())) return InteractionResult.PASS; // เจ้าของเปิดหีบได้

        Item item = ShopCatalog.itemById(shop.item);
        BlockEntity be = level.getBlockEntity(pos);
        if (item == null || !(be instanceof Container chest)) {
            Gui.msg(sp, "§c[ตู้ขายของ] ตู้นี้ใช้งานไม่ได้ในตอนนี้");
            return InteractionResult.SUCCESS;
        }
        int stock = countPlain(chest, item);
        if (stock <= 0) {
            Gui.msg(sp, "§c[ตู้ขายของ] สินค้าหมด");
            return InteractionResult.SUCCESS;
        }
        int n = Math.min(sp.isShiftKeyDown() ? 16 : 1, stock);
        long price = shop.price * n;
        long now = System.currentTimeMillis();
        int tax = MoneyMod.taxFor(owner);
        try {
            MoneyMod.bank().sale(sp.getUUID(), owner, price, tax,
                    "ซื้อ " + shop.item + " x" + n + " (ตู้ขายของ)", "ขาย " + shop.item + " x" + n + " (ตู้ขายของ)");
            removePlain(chest, item, n); // จ่ายเงินสำเร็จแล้วจึงเอาของออกจากหีบ
            chest.setChanged();
            CashUtil.giveItem(sp, item, n);
            Gui.msg(sp, "§a[ตู้ขายของ] ซื้อ " + shop.item + " x" + n + " ราคา ฿" + price);
            ServerPlayer seller = MoneyMod.server().getPlayerList().getPlayer(owner);
            if (seller != null) {
                Gui.msg(seller, "§a[ตู้ขายของ] " + sp.getName().getString() + " ซื้อ " + shop.item + " x" + n + " (฿" + price + ")");
            }
        } catch (BankException e) {
            Gui.msg(sp, "§c[ตู้ขายของ] " + e.getMessage());
        }
        return InteractionResult.SUCCESS;
    }

    public static int countPlain(Container c, Item item) {
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (s.is(item) && s.getComponentsPatch().isEmpty()) n += s.getCount();
        }
        return n;
    }

    private static void removePlain(Container c, Item item, int max) {
        int left = max;
        for (int i = 0; i < c.getContainerSize() && left > 0; i++) {
            ItemStack s = c.getItem(i);
            if (s.is(item) && s.getComponentsPatch().isEmpty()) {
                int take = Math.min(s.getCount(), left);
                c.removeItem(i, take);
                left -= take;
            }
        }
    }

    /** กันทุบตู้ขายของ: เฉพาะเจ้าของทุบได้ (และเลิกเป็นตู้ขายของ) */
    public static boolean allowBreak(Level level, Player player, BlockPos pos) {
        String key = keyOf(level, pos);
        ChestShops.Shop shop = MoneyMod.chestShops().get(key);
        if (shop == null) return true;
        if (shop.owner.equals(player.getUUID().toString())) {
            MoneyMod.chestShops().remove(key);
            if (player instanceof ServerPlayer sp) Gui.msg(sp, "§e[ตู้ขายของ] ยกเลิกตู้ขายของนี้แล้ว");
            return true;
        }
        if (player instanceof ServerPlayer sp) Gui.msg(sp, "§c[ตู้ขายของ] ตู้นี้เป็นของ " + shop.ownerName + " ทุบไม่ได้");
        return false;
    }
}
