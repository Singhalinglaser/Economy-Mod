package com.thaimoney.gui;

import com.thaimoney.CashUtil;
import com.thaimoney.ModItems;
import com.thaimoney.MoneyMod;
import com.thaimoney.PinGuard;
import com.thaimoney.core.Bank;
import com.thaimoney.core.BankConfig;
import com.thaimoney.core.BankException;
import com.thaimoney.core.Denomination;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** ตู้ ATM: PIN → หน้าหลัก (ฝาก/ถอน/โอน/ประวัติ/เปลี่ยน PIN) ทำงานในเมนูเดียว เปลี่ยนหน้าโดยวาดใหม่ */
public final class AtmMenu extends GuiMenu {
    private enum Phase { PIN_SETUP_1, PIN_SETUP_2, PIN_ENTER, PIN_CHANGE_OLD, HOME, DEPOSIT, AMOUNT, PICK_PLAYER, HISTORY }

    private enum Mode { WITHDRAW, TRANSFER }

    /** ช่องของปุ่มตัวเลข index = เลข */
    private static final int[] DIGIT_SLOTS = {40, 12, 13, 14, 21, 22, 23, 30, 31, 32};
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.systemDefault());

    private final UUID me;
    private final Bank bank = MoneyMod.bank();
    private final BankConfig cfg = MoneyMod.bank().config();

    private Phase phase;
    private boolean authed;
    private String entry = "";
    private String firstPin;
    private Mode mode = Mode.WITHDRAW;
    private long amount;
    private UUID target;
    private String targetName = "";
    private Denomination largest = Denomination.NOTE_1000;

    public static void open(ServerPlayer p) {
        p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new AtmMenu(id, inv, (ServerPlayer) pl),
                Component.literal("ATM")));
    }

    private AtmMenu(int syncId, Inventory inv, ServerPlayer p) {
        super(syncId, inv, 6);
        this.me = p.getUUID();
        this.phase = MoneyMod.pins().has(me) ? Phase.PIN_ENTER : Phase.PIN_SETUP_1;
        render();
    }

    private static long today() {
        return MoneyMod.today();
    }

    // ------------------------------------------------------------------ วาดหน้าจอ
    private void render() {
        if (!authed && phase != Phase.PIN_SETUP_1 && phase != Phase.PIN_SETUP_2
                && phase != Phase.PIN_ENTER && phase != Phase.PIN_CHANGE_OLD) {
            phase = Phase.PIN_ENTER;
        }
        clearAll();
        fillPanes();
        switch (phase) {
            case PIN_SETUP_1 -> keypad("ตั้ง PIN ใหม่ (4 หลัก)", "§7กรอกเลข 4 หลักที่ต้องการ");
            case PIN_SETUP_2 -> keypad("ยืนยัน PIN อีกครั้ง", "§7กรอกเลขเดิมซ้ำ");
            case PIN_ENTER -> keypad("กรอก PIN", "§7ผิด " + cfg.pinMaxAttempts + " ครั้งจะถูกล็อก " + cfg.pinLockMinutes + " นาที");
            case PIN_CHANGE_OLD -> keypad("กรอก PIN เดิม", "§7เพื่อยืนยันก่อนเปลี่ยน PIN");
            case HOME -> home();
            case DEPOSIT -> deposit();
            case AMOUNT -> amountPad();
            case PICK_PLAYER -> pickPlayer();
            case HISTORY -> history();
        }
    }

    private void keypad(String title, String hint) {
        String masked = "●".repeat(entry.length()) + "○".repeat(4 - entry.length());
        set(4, Gui.icon(Items.NAME_TAG, title, "§f" + masked, hint));
        for (int d = 0; d <= 9; d++) {
            final int digit = d;
            button(DIGIT_SLOTS[d], Gui.icon(Items.STONE_BUTTON, String.valueOf(d)), (p, t, b) -> onDigit(p, digit));
        }
        button(39, Gui.icon(Items.RED_DYE, "ล้าง"), (p, t, b) -> {
            entry = "";
            render();
        });
        button(41, Gui.icon(Items.BARRIER, "ยกเลิก"), (p, t, b) -> p.closeContainer());
    }

    private void onDigit(ServerPlayer p, int d) {
        if (entry.length() >= 4) return;
        entry += d;
        if (entry.length() < 4) {
            render();
            return;
        }
        String pin = entry;
        entry = "";
        switch (phase) {
            case PIN_SETUP_1 -> {
                firstPin = pin;
                phase = Phase.PIN_SETUP_2;
            }
            case PIN_SETUP_2 -> {
                if (pin.equals(firstPin)) {
                    try {
                        MoneyMod.pins().set(me, pin);
                        authed = true;
                        phase = Phase.HOME;
                        Gui.msg(p, "§a[ATM] ตั้ง PIN เรียบร้อย");
                    } catch (BankException e) {
                        Gui.msg(p, "§c" + e.getMessage());
                        phase = Phase.PIN_SETUP_1;
                    }
                } else {
                    Gui.msg(p, "§c[ATM] PIN ไม่ตรงกัน ลองใหม่");
                    phase = Phase.PIN_SETUP_1;
                }
                firstPin = null;
            }
            case PIN_ENTER, PIN_CHANGE_OLD -> {
                String err = PinGuard.check(me, pin);
                if (err == null) {
                    authed = true;
                    phase = phase == Phase.PIN_ENTER ? Phase.HOME : Phase.PIN_SETUP_1;
                } else {
                    Gui.msg(p, "[ATM] " + err);
                    if (PinGuard.isLocked(me)) {
                        p.closeContainer();
                        return;
                    }
                }
            }
            default -> { }
        }
        render();
    }

    private void home() {
        long bal = bank.balance(me);
        long left = Math.max(0, cfg.dailyWithdrawLimit - bank.withdrawnToday(me, today()));
        set(4, Gui.icon(Items.GOLD_INGOT, "ยอดคงเหลือ: ฿" + bal,
                "§7ถอนได้อีกวันนี้: ฿" + left,
                "§7ค่าธรรมเนียมถอน " + Gui.pct(cfg.withdrawFeeBp) + " | โอน " + Gui.pct(cfg.transferFeeBp)));
        button(19, Gui.icon(ModItems.cash(Denomination.NOTE_100), "ฝากเงิน", "§7ฝากเงินสดเข้าบัญชี"), (p, t, b) -> {
            phase = Phase.DEPOSIT;
            render();
        });
        button(21, Gui.icon(ModItems.cash(Denomination.NOTE_1000), "ถอนเงิน", "§7ถอนเป็นเงินสด (แตกแบงก์อัตโนมัติ)"), (p, t, b) -> {
            mode = Mode.WITHDRAW;
            amount = 0;
            phase = Phase.AMOUNT;
            render();
        });
        button(23, Gui.icon(Items.ENDER_PEARL, "โอนเงิน", "§7โอนให้ผู้เล่นที่ออนไลน์"), (p, t, b) -> {
            phase = Phase.PICK_PLAYER;
            render();
        });
        button(25, Gui.icon(Items.BOOK, "ประวัติธุรกรรม", "§7รายการล่าสุด 30 รายการ"), (p, t, b) -> {
            phase = Phase.HISTORY;
            render();
        });
        button(40, Gui.icon(Items.TRIPWIRE_HOOK, "เปลี่ยน PIN"), (p, t, b) -> {
            entry = "";
            phase = Phase.PIN_CHANGE_OLD;
            render();
        });
        button(49, Gui.icon(Items.BARRIER, "ปิด"), (p, t, b) -> p.closeContainer());
    }

    private void backButton() {
        button(49, Gui.icon(Items.ARROW, "ย้อนกลับ"), (p, t, b) -> {
            phase = Phase.HOME;
            amount = 0;
            render();
        });
    }

    private void deposit() {
        set(4, Gui.icon(Items.GOLD_INGOT, "ยอดคงเหลือ: ฿" + bank.balance(me)));
        button(20, Gui.icon(ModItems.cash(Denomination.NOTE_100), "ฝากเงินสดทั้งหมดในตัว",
                "§7ฝากเงินสดทั้งหมดที่พกอยู่ในช่องเก็บของ"), (p, t, b) -> {
            long cash = CashUtil.countCash(p.getInventory());
            if (cash <= 0) {
                Gui.msg(p, "§c[ATM] คุณไม่มีเงินสดในตัว");
            } else {
                try {
                    bank.deposit(me, cash); // สำเร็จก่อนค่อยหักเงินสด
                    CashUtil.takeAllCash(p.getInventory());
                    Gui.msg(p, "§a[ATM] ฝาก ฿" + cash + " แล้ว");
                } catch (BankException e) {
                    Gui.msg(p, "§c[ATM] " + e.getMessage());
                }
            }
            render();
        });
        button(24, Gui.icon(ModItems.WALLET, "ฝากเงินจากกระเป๋าสตางค์",
                "§7ต้องมีกระเป๋าสตางค์ติดตัว", "§7ในกระเป๋ามี: ฿" + MoneyMod.wallets().total(me)), (p, t, b) -> {
            if (!CashUtil.hasWallet(p.getInventory())) {
                Gui.msg(p, "§c[ATM] ต้องมีกระเป๋าสตางค์ติดตัว");
            } else {
                long w = MoneyMod.wallets().total(me);
                if (w <= 0) {
                    Gui.msg(p, "§c[ATM] กระเป๋าว่างเปล่า");
                } else {
                    try {
                        bank.deposit(me, w);
                        MoneyMod.wallets().clear(me);
                        Gui.msg(p, "§a[ATM] ฝาก ฿" + w + " จากกระเป๋าแล้ว");
                    } catch (BankException e) {
                        Gui.msg(p, "§c[ATM] " + e.getMessage());
                    }
                }
            }
            render();
        });
        backButton();
    }

    private void amountPad() {
        int bp = mode == Mode.WITHDRAW ? cfg.withdrawFeeBp : cfg.transferFeeBp;
        long fee = Bank.fee(amount, bp);
        String title = mode == Mode.WITHDRAW ? "ถอนเงิน" : "โอนให้ " + targetName;
        set(4, Gui.icon(Items.NAME_TAG, title + ": ฿" + amount,
                "§7ค่าธรรมเนียม: ฿" + fee, "§7หักจากบัญชีรวม: ฿" + (amount + fee), "§7คงเหลือ: ฿" + bank.balance(me)));

        long[] steps = {1, 10, 100, 1000, 10000};
        Item[] icons = {ModItems.cash(Denomination.COIN_1), ModItems.cash(Denomination.COIN_10),
                ModItems.cash(Denomination.NOTE_100), ModItems.cash(Denomination.NOTE_1000), Items.GOLD_BLOCK};
        for (int i = 0; i < steps.length; i++) {
            final long step = steps[i];
            button(10 + i, Gui.icon(icons[i], "฿" + step, "§7คลิกซ้าย: +" + step, "§7คลิกขวา: -" + step), (p, t, b) -> {
                amount = b == 1 ? Math.max(0, amount - step) : Math.min(cfg.maxTransaction, amount + step);
                render();
            });
        }
        button(28, Gui.icon(Items.RED_DYE, "ล้างจำนวน"), (p, t, b) -> {
            amount = 0;
            render();
        });
        button(30, Gui.icon(Items.NETHER_STAR, "สูงสุด", "§7ใช้ยอดทั้งหมดเท่าที่ทำได้"), (p, t, b) -> {
            amount = maxAmount(bp);
            render();
        });
        if (mode == Mode.WITHDRAW) {
            button(32, Gui.icon(ModItems.cash(largest), "แบงก์ใหญ่สุดที่รับ: ฿" + largest.value,
                    "§7คลิกเพื่อสลับชนิด"), (p, t, b) -> {
                List<Denomination> order = Denomination.descending();
                largest = order.get((order.indexOf(largest) + 1) % order.size());
                render();
            });
        }
        button(34, Gui.icon(Items.EMERALD, "ยืนยัน", "§7" + title + " ฿" + amount), (p, t, b) -> confirm(p));
        backButton();
    }

    private long maxAmount(int bp) {
        long cap = bank.balance(me);
        long m = cap * 10_000L / (10_000L + bp);
        while (m > 0 && m + Bank.fee(m, bp) > cap) m--;
        if (mode == Mode.WITHDRAW) {
            m = Math.min(m, Math.max(0, cfg.dailyWithdrawLimit - bank.withdrawnToday(me, today())));
        }
        return Math.min(m, cfg.maxTransaction);
    }

    private void confirm(ServerPlayer p) {
        if (amount <= 0) {
            Gui.msg(p, "§c[ATM] ใส่จำนวนเงินก่อน");
            return;
        }
        try {
            if (mode == Mode.WITHDRAW) {
                long fee = bank.withdraw(me, amount, today());
                CashUtil.give(p, amount, largest);
                Gui.msg(p, "§a[ATM] ถอน ฿" + amount + " (ค่าธรรมเนียม ฿" + fee + ") คงเหลือ ฿" + bank.balance(me));
            } else {
                long fee = bank.transfer(me, target, amount, "โอนให้ " + targetName,
                        "รับโอนจาก " + p.getName().getString());
                Gui.msg(p, "§a[ATM] โอน ฿" + amount + " ให้ " + targetName + " แล้ว (ค่าธรรมเนียม ฿" + fee + ")");
                ServerPlayer receiver = MoneyMod.server().getPlayerList().getPlayer(target);
                if (receiver != null) {
                    Gui.msg(receiver, "§a" + p.getName().getString() + " โอนเงินให้คุณ ฿" + amount);
                }
            }
            amount = 0;
            phase = Phase.HOME;
        } catch (BankException e) {
            Gui.msg(p, "§c[ATM] " + e.getMessage());
        }
        render();
    }

    private static List<Integer> gridSlots() {
        List<Integer> slots = new ArrayList<>();
        for (int row = 1; row <= 4; row++) for (int col = 1; col <= 7; col++) slots.add(row * 9 + col);
        return slots;
    }

    private void pickPlayer() {
        set(4, Gui.icon(Items.NAME_TAG, "เลือกผู้รับ", "§7ผู้เล่นที่ออนไลน์อยู่"));
        List<Integer> slots = gridSlots();
        int i = 0;
        for (ServerPlayer other : MoneyMod.server().getPlayerList().getPlayers()) {
            if (other.getUUID().equals(me) || i >= slots.size()) continue;
            final UUID id = other.getUUID();
            final String name = other.getName().getString();
            button(slots.get(i++), Gui.icon(Items.PLAYER_HEAD, name, "§7คลิกเพื่อโอน"), (p, t, b) -> {
                target = id;
                targetName = name;
                mode = Mode.TRANSFER;
                amount = 0;
                phase = Phase.AMOUNT;
                render();
            });
        }
        if (i == 0) set(22, Gui.icon(Items.BARRIER, "ไม่มีผู้เล่นอื่นออนไลน์"));
        backButton();
    }

    private void history() {
        set(4, Gui.icon(Items.BOOK, "ประวัติธุรกรรม"));
        List<String> rows = bank.history(me);
        List<Integer> slots = gridSlots();
        for (int i = 0; i < rows.size() && i < slots.size(); i++) {
            String[] parts = rows.get(i).split("\\|", 3);
            if (parts.length < 3) continue;
            long ts = Long.parseLong(parts[0]);
            long delta = Long.parseLong(parts[1]);
            String sign = delta >= 0 ? "§a+฿" + delta : "§c-฿" + Math.abs(delta);
            set(slots.get(i), Gui.icon(delta >= 0 ? Items.LIME_DYE : Items.RED_DYE, sign,
                    "§f" + parts[2], "§7" + TIME.format(Instant.ofEpochMilli(ts))));
        }
        if (rows.isEmpty()) set(22, Gui.icon(Items.BARRIER, "ยังไม่มีรายการ"));
        backButton();
    }
}
