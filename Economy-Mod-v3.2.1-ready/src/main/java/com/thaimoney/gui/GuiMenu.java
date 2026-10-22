package com.thaimoney.gui;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

/**
 * เมนูหีบฝั่งเซิร์ฟเวอร์ล้วน: ทุกคลิกถูกยกเลิก (ไอเทมในเมนูหยิบออกไม่ได้) แล้วเรียกปุ่มที่ผูกไว้กับช่องนั้นแทน
 */
public abstract class GuiMenu extends ChestMenu {
    @FunctionalInterface
    public interface Action {
        void run(ServerPlayer player, ClickType type, int button);
    }

    protected final SimpleContainer box;
    private final Map<Integer, Action> actions = new HashMap<>();

    protected GuiMenu(int syncId, Inventory inv, int rows) {
        this(syncId, inv, new SimpleContainer(rows * 9), rows);
    }

    private GuiMenu(int syncId, Inventory inv, SimpleContainer box, int rows) {
        super(typeFor(rows), syncId, inv, box, rows);
        this.box = box;
    }

    private static MenuType<?> typeFor(int rows) {
        return switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
    }

    protected void clearAll() {
        box.clearContent();
        actions.clear();
    }

    protected void fillPanes() {
        for (int i = 0; i < box.getContainerSize(); i++) box.setItem(i, Gui.pane());
    }

    protected void set(int slot, ItemStack stack) {
        if (slot >= 0 && slot < box.getContainerSize()) box.setItem(slot, stack);
    }

    protected void button(int slot, ItemStack stack, Action action) {
        set(slot, stack);
        actions.put(slot, action);
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < box.getContainerSize() && player instanceof ServerPlayer sp) {
            Action a = actions.get(slotId);
            if (a != null) a.run(sp, clickType, button);
        }
        // ไม่เรียก super: ไม่ให้ใครหยิบ/วาง/ลากไอเทมในเมนู แล้วซิงก์ฝั่งไคลเอนต์กลับ
        this.sendAllDataToRemote();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
