package com.thaimoney;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.thaimoney.core.Bank;
import com.thaimoney.core.BankException;
import com.thaimoney.core.Denomination;
import com.thaimoney.core.Market;
import com.thaimoney.core.Auctions;
import com.thaimoney.core.ChestShops;
import com.thaimoney.gui.AdminShopMenu;
import com.thaimoney.gui.Gui;
import com.thaimoney.gui.AuctionMenu;
import com.thaimoney.gui.MarketMenu;
import com.thaimoney.gui.WalletMenu;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;

public final class MoneyCommands {
    private MoneyCommands() {}

    private static Bank bank() {
        return MoneyMod.bank();
    }

    private static void ok(CommandContext<CommandSourceStack> ctx, String msg) {
        ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
    }

    private static int fail(CommandContext<CommandSourceStack> ctx, String msg) {
        ctx.getSource().sendFailure(Component.literal(msg));
        return 0;
    }

    private static int fail(CommandContext<CommandSourceStack> ctx, BankException e) {
        return fail(ctx, e.getMessage());
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("bal").executes(MoneyCommands::bal));
        d.register(Commands.literal("balance").executes(MoneyCommands::bal));

        // /pay <ผู้เล่น> <จำนวน> [pin]  (ถ้าตั้ง PIN ไว้ ต้องใส่ PIN)
        d.register(Commands.literal("pay")
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                .executes(ctx -> pay(ctx, null))
                                .then(Commands.argument("pin", StringArgumentType.word())
                                        .executes(ctx -> pay(ctx, StringArgumentType.getString(ctx, "pin")))))));

        d.register(Commands.literal("deposit")
                .then(Commands.literal("all").executes(ctx -> deposit(ctx, -1)))
                .then(Commands.argument("amount", LongArgumentType.longArg(1))
                        .executes(ctx -> deposit(ctx, LongArgumentType.getLong(ctx, "amount")))));

        d.register(Commands.literal("withdraw")
                .then(Commands.argument("amount", LongArgumentType.longArg(1))
                        .executes(ctx -> withdraw(ctx, Denomination.NOTE_1000))
                        .then(Commands.argument("largest", LongArgumentType.longArg(1))
                                .executes(MoneyCommands::withdrawWithLimit))));

        d.register(Commands.literal("wallet").executes(ctx -> {
            WalletMenu.open(ctx.getSource().getPlayerOrException());
            return 1;
        }));

        d.register(Commands.literal("shop").executes(ctx -> {
            AdminShopMenu.open(ctx.getSource().getPlayerOrException());
            return 1;
        }));

        d.register(Commands.literal("market")
                .executes(ctx -> {
                    MarketMenu.open(ctx.getSource().getPlayerOrException());
                    return 1;
                })
                .then(Commands.literal("sell")
                        .then(Commands.argument("price", LongArgumentType.longArg(1)).executes(MoneyCommands::marketSell))));

        // ประมูล
        d.register(Commands.literal("auction")
                .executes(ctx -> {
                    AuctionMenu.open(ctx.getSource().getPlayerOrException());
                    return 1;
                })
                .then(Commands.literal("start")
                        .then(Commands.argument("price", LongArgumentType.longArg(1))
                                .then(Commands.argument("minutes", IntegerArgumentType.integer(1))
                                        .executes(MoneyCommands::auctionStart))))
                .then(Commands.literal("bid")
                        .then(Commands.argument("id", LongArgumentType.longArg(1))
                                .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                        .executes(MoneyCommands::auctionBid)))));

        // รับของจากกล่องรับของ (ชนะประมูล / ประมูลไม่มีผู้ซื้อ / ยกเลิก)
        d.register(Commands.literal("claim").executes(MoneyCommands::claim));

        // แผงตลาด
        d.register(Commands.literal("stall")
                .executes(MoneyCommands::stallInfo)
                .then(Commands.literal("rent")
                        .then(Commands.literal("day").executes(ctx -> stallRent(ctx, 1)))
                        .then(Commands.literal("week").executes(ctx -> stallRent(ctx, 7)))));

        // ตู้ขายของ (เล็งที่หีบ ถือไอเทมที่จะขายในมือหลัก)
        d.register(Commands.literal("chestshop")
                .then(Commands.literal("create")
                        .then(Commands.argument("price", LongArgumentType.longArg(1)).executes(MoneyCommands::chestShopCreate)))
                .then(Commands.literal("remove").executes(MoneyCommands::chestShopRemove)));

        // แอดมิน: /admin money give|take|set <ผู้เล่น> <จำนวน>
        var admin = Commands.literal("admin")
                .requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_MODERATOR));
        var money = Commands.literal("money");
        for (String op : new String[] {"give", "take", "set"}) {
            money.then(Commands.literal(op)
                    .then(Commands.argument("player", EntityArgument.player())
                            .then(Commands.argument("amount", LongArgumentType.longArg(0))
                                    .executes(ctx -> adminOp(ctx, op)))));
        }
        var zone = Commands.literal("marketzone")
                .then(Commands.literal("add")
                        .then(Commands.argument("from", BlockPosArgument.blockPos())
                                .then(Commands.argument("to", BlockPosArgument.blockPos())
                                        .executes(MoneyCommands::zoneAdd))))
                .then(Commands.literal("list").executes(MoneyCommands::zoneList))
                .then(Commands.literal("remove")
                        .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                .executes(MoneyCommands::zoneRemove)));
        d.register(admin.then(money).then(zone));
    }

    private static int bal(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        long b = bank().balance(p.getUUID());
        long cash = CashUtil.countCash(p.getInventory());
        ok(ctx, "§6ยอดในบัญชี: §a฿" + b + " §7| เงินสดในตัว: ฿" + cash);
        int pending = MoneyMod.mailbox().pending(p.getUUID());
        if (pending > 0) ok(ctx, "§eคุณมีของรอรับ " + pending + " ชิ้น พิมพ์ /claim");
        return 1;
    }

    private static int pay(CommandContext<CommandSourceStack> ctx, String pin) throws CommandSyntaxException {
        ServerPlayer from = ctx.getSource().getPlayerOrException();
        ServerPlayer to = EntityArgument.getPlayer(ctx, "player");
        long amount = LongArgumentType.getLong(ctx, "amount");
        if (MoneyMod.pins().has(from.getUUID())) {
            if (pin == null) return fail(ctx, "คุณตั้ง PIN ไว้ ใช้: /pay <ผู้เล่น> <จำนวน> <PIN>");
            String err = PinGuard.check(from.getUUID(), pin);
            if (err != null) return fail(ctx, err);
        }
        try {
            long fee = bank().transfer(from.getUUID(), to.getUUID(), amount,
                    "โอนให้ " + to.getName().getString(), "รับโอนจาก " + from.getName().getString());
            ok(ctx, "§aโอน ฿" + amount + " ให้ " + to.getName().getString() + " แล้ว §7(ค่าธรรมเนียม ฿" + fee + ")");
            to.sendSystemMessage(Component.literal("§a" + from.getName().getString() + " โอนเงินให้คุณ ฿" + amount));
            return 1;
        } catch (BankException e) {
            return fail(ctx, e);
        }
    }

    /** amount = -1 หมายถึงฝากทั้งหมด ถ้าฝากบางส่วนจะทอนเงินสดคืนเป็นแบงก์ */
    private static int deposit(CommandContext<CommandSourceStack> ctx, long amount) throws CommandSyntaxException {
        if (bank().config().requireAtmForCash) return fail(ctx, "ใช้ตู้ ATM แทน (คลิกขวาที่ ATM)");
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        long cash = CashUtil.countCash(p.getInventory());
        long want = amount < 0 ? cash : amount;
        if (cash <= 0 || want <= 0) return fail(ctx, "คุณไม่มีเงินสดในตัว");
        if (want > cash) return fail(ctx, "เงินสดไม่พอ (มี ฿" + cash + ")");
        try {
            bank().deposit(p.getUUID(), want); // สำเร็จก่อนค่อยหักเงินสด
            CashUtil.takeAllCash(p.getInventory());
            if (cash > want) CashUtil.give(p, cash - want, Denomination.NOTE_1000);
            ok(ctx, "§aฝาก ฿" + want + " แล้ว ยอดใหม่ ฿" + bank().balance(p.getUUID()));
            return 1;
        } catch (BankException e) {
            return fail(ctx, e);
        }
    }

    private static int withdrawWithLimit(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Denomination d = Denomination.byValue(LongArgumentType.getLong(ctx, "largest"));
        if (d == null) return fail(ctx, "ไม่มีเงินชนิดนี้ (ใช้ได้: 1,2,5,10,20,50,100,500,1000)");
        return withdraw(ctx, d);
    }

    private static int withdraw(CommandContext<CommandSourceStack> ctx, Denomination largest) throws CommandSyntaxException {
        if (bank().config().requireAtmForCash) return fail(ctx, "ใช้ตู้ ATM แทน (คลิกขวาที่ ATM)");
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        long amount = LongArgumentType.getLong(ctx, "amount");
        try {
            long fee = bank().withdraw(p.getUUID(), amount, MoneyMod.today());
            CashUtil.give(p, amount, largest);
            ok(ctx, "§aถอน ฿" + amount + " แล้ว §7(ค่าธรรมเนียม ฿" + fee + ") §fคงเหลือ ฿" + bank().balance(p.getUUID()));
            return 1;
        } catch (BankException e) {
            return fail(ctx, e);
        }
    }

    private static int marketSell(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        long price = LongArgumentType.getLong(ctx, "price");
        if (!MoneyMod.inMarketZone(p)) return fail(ctx, MoneyMod.ZONE_MESSAGE);
        ItemStack held = p.getMainHandItem();
        if (held.isEmpty()) return fail(ctx, "ถือไอเทมที่จะขายไว้ในมือหลัก");
        if (held.getItem() instanceof CashItem || held.is(ModItems.WALLET)) return fail(ctx, "ขายเงินสด/กระเป๋าสตางค์ไม่ได้");
        if (!held.getComponentsPatch().isEmpty()) {
            return fail(ctx, "ยังขายไอเทมที่มีชื่อ/เอนแชนต์/ความทนทานลดไม่ได้ (เวอร์ชันนี้ขายได้เฉพาะไอเทมธรรมดา)");
        }
        String itemId = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
        try {
            Market.Listing l = MoneyMod.market().create(p.getUUID(), p.getName().getString(), itemId, held.getCount(),
                    price, MoneyMod.maxListingsFor(p.getUUID()), System.currentTimeMillis(), bank().config().maxTransaction);
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY); // ลงขายสำเร็จแล้วจึงเอาของออกจากมือ
            ok(ctx, "§aลงขาย " + itemId + " x" + l.count + " ราคา ฿" + l.price + " แล้ว (/market เพื่อดู)");
            return 1;
        } catch (BankException e) {
            return fail(ctx, e);
        }
    }

    private static int auctionStart(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        long price = LongArgumentType.getLong(ctx, "price");
        int minutes = IntegerArgumentType.getInteger(ctx, "minutes");
        var cfg = bank().config();
        if (!MoneyMod.inMarketZone(p)) return fail(ctx, MoneyMod.ZONE_MESSAGE);
        if (minutes > cfg.auctionMaxMinutes) return fail(ctx, "ตั้งเวลาได้สูงสุด " + cfg.auctionMaxMinutes + " นาที");
        String err = sellableError(p.getMainHandItem());
        if (err != null) return fail(ctx, err);
        ItemStack held = p.getMainHandItem();
        String itemId = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
        try {
            Auctions.Auction a = MoneyMod.auctions().create(p.getUUID(), p.getName().getString(), itemId, held.getCount(),
                    price, minutes * 60_000L, System.currentTimeMillis(), cfg.maxAuctionsPerPlayer, cfg.maxTransaction);
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY); // เปิดประมูลสำเร็จแล้วจึงเอาของออกจากมือ
            ok(ctx, "§aเปิดประมูล #" + a.id + " " + itemId + " x" + a.count + " เริ่ม ฿" + price + " นาน " + minutes + " นาที");
            return 1;
        } catch (BankException e) {
            return fail(ctx, e);
        }
    }

    private static int auctionBid(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        long id = LongArgumentType.getLong(ctx, "id");
        long amount = LongArgumentType.getLong(ctx, "amount");
        try {
            MoneyMod.auctions().bid(bank(), id, p.getUUID(), p.getName().getString(), amount, System.currentTimeMillis());
            ok(ctx, "§aเสนอราคา ฿" + amount + " สำเร็จ (กันเงินไว้ คืนอัตโนมัติถ้าถูกเสนอสูงกว่า)");
            return 1;
        } catch (BankException e) {
            return fail(ctx, e);
        }
    }

    private static int claim(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        var items = MoneyMod.mailbox().drain(p.getUUID());
        if (items.isEmpty()) return fail(ctx, "ไม่มีของรอรับ");
        int total = 0;
        for (var e : items) {
            Item item = ShopCatalog.itemById(e.item);
            if (item == null) {
                MoneyMod.mailbox().add(p.getUUID(), e.item, e.count); // ไอเทมที่ไม่รู้จัก เก็บไว้ก่อน
                continue;
            }
            CashUtil.giveItem(p, item, e.count);
            total += e.count;
        }
        ok(ctx, "§aรับของแล้ว " + total + " ชิ้น");
        return 1;
    }

    private static int stallInfo(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        var cfg = bank().config();
        long now = System.currentTimeMillis();
        long exp = MoneyMod.stalls().expiresAt(p.getUUID());
        String status = exp > now ? "§aเช่าอยู่ อีก " + AuctionMenu.timeLeft(exp - now) : "§7ยังไม่ได้เช่า";
        ok(ctx, "§6แผงตลาด: " + status);
        ok(ctx, "§7ระหว่างเช่า: ลงขายได้ " + cfg.stallMaxListings + " รายการ (ปกติ " + cfg.maxListingsPerPlayer
                + ") และภาษีขาย " + Gui.pct(cfg.stallTaxBp) + " (ปกติ " + Gui.pct(cfg.saleTaxBp) + ")");
        ok(ctx, "§7ราคา: /stall rent day = ฿" + cfg.stallRentPerDay + " | /stall rent week = ฿" + cfg.stallRentPerWeek);
        return 1;
    }

    private static int stallRent(CommandContext<CommandSourceStack> ctx, int days) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        var cfg = bank().config();
        if (!MoneyMod.inMarketZone(p)) return fail(ctx, MoneyMod.ZONE_MESSAGE);
        long price = days == 1 ? cfg.stallRentPerDay : cfg.stallRentPerWeek;
        try {
            MoneyMod.stalls().rent(bank(), p.getUUID(), days, price, System.currentTimeMillis());
            ok(ctx, "§aเช่าแผงตลาด " + days + " วัน ราคา ฿" + price + " แล้ว");
            return 1;
        } catch (BankException e) {
            return fail(ctx, e);
        }
    }

    /** คืนข้อความผิดพลาดถ้าไอเทมในมือขายไม่ได้ (v1 ขายได้เฉพาะไอเทมธรรมดา) */
    private static String sellableError(ItemStack held) {
        if (held.isEmpty()) return "ถือไอเทมไว้ในมือหลัก";
        if (held.getItem() instanceof CashItem || held.is(ModItems.WALLET)) return "เงินสด/กระเป๋าสตางค์ใช้ไม่ได้";
        if (!held.getComponentsPatch().isEmpty()) {
            return "ยังใช้ไอเทมที่มีชื่อ/เอนแชนต์/ความทนทานลดไม่ได้ (เวอร์ชันนี้รองรับเฉพาะไอเทมธรรมดา)";
        }
        return null;
    }

    private static BlockHitResult lookedAtChest(ServerPlayer p) {
        HitResult hr = p.pick(5.0, 1.0f, false);
        if (hr.getType() != HitResult.Type.BLOCK || !(hr instanceof BlockHitResult bhr)) return null;
        return p.level().getBlockEntity(bhr.getBlockPos()) instanceof ChestBlockEntity ? bhr : null;
    }

    private static int chestShopCreate(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        long price = LongArgumentType.getLong(ctx, "price");
        if (!MoneyMod.inMarketZone(p)) return fail(ctx, MoneyMod.ZONE_MESSAGE);
        BlockHitResult hit = lookedAtChest(p);
        if (hit == null) return fail(ctx, "เล็งที่หีบ (ห่างไม่เกิน 5 บล็อก)");
        String err = sellableError(p.getMainHandItem());
        if (err != null) return fail(ctx, err);
        String itemId = BuiltInRegistries.ITEM.getKey(p.getMainHandItem().getItem()).toString();
        String key = ChestShopHandler.keyOf(p.level(), hit.getBlockPos());
        try {
            ChestShops.Shop s = MoneyMod.chestShops().create(p.getUUID(), p.getName().getString(), key, itemId, price,
                    bank().config().chestShopMaxPerPlayer, bank().config().maxTransaction);
            ok(ctx, "§aสร้างตู้ขายของ: " + s.item + " ชิ้นละ ฿" + s.price + " (เติมของในหีบได้เลย ผู้ซื้อคลิกขวาที่หีบ)");
            return 1;
        } catch (BankException e) {
            return fail(ctx, e);
        }
    }

    private static int chestShopRemove(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        BlockHitResult hit = lookedAtChest(p);
        if (hit == null) return fail(ctx, "เล็งที่หีบ (ห่างไม่เกิน 5 บล็อก)");
        String key = ChestShopHandler.keyOf(p.level(), hit.getBlockPos());
        ChestShops.Shop s = MoneyMod.chestShops().get(key);
        if (s == null) return fail(ctx, "หีบนี้ไม่ใช่ตู้ขายของ");
        boolean isAdmin = ctx.getSource().permissions().hasPermission(Permissions.COMMANDS_MODERATOR);
        if (!s.owner.equals(p.getUUID().toString()) && !isAdmin) return fail(ctx, "ไม่ใช่ตู้ของคุณ");
        MoneyMod.chestShops().remove(key);
        ok(ctx, "§eยกเลิกตู้ขายของแล้ว");
        return 1;
    }

    private static int zoneAdd(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        BlockPos a = BlockPosArgument.getBlockPos(ctx, "from");
        BlockPos b = BlockPosArgument.getBlockPos(ctx, "to");
        String dim = ctx.getSource().getLevel().dimension().toString();
        MoneyMod.zones().add(dim, a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ());
        ok(ctx, "§aเพิ่มโซนตลาดแล้ว (" + a.getX() + "," + a.getY() + "," + a.getZ() + ") ถึง ("
                + b.getX() + "," + b.getY() + "," + b.getZ() + ") ตอนนี้ลงขาย/เปิดประมูล/เช่าแผง/ตั้งตู้ขายของได้เฉพาะในโซนตลาด");
        return 1;
    }

    private static int zoneList(CommandContext<CommandSourceStack> ctx) {
        var list = MoneyMod.zones().snapshot();
        if (list.isEmpty()) {
            ok(ctx, "§7ยังไม่มีโซนตลาด (ลงขายได้ทุกที่)");
            return 1;
        }
        int i = 1;
        for (var z : list) {
            ok(ctx, "§6#" + (i++) + " §f" + z.dimension + " (" + z.minX + "," + z.minY + "," + z.minZ + ") - ("
                    + z.maxX + "," + z.maxY + "," + z.maxZ + ")");
        }
        return list.size();
    }

    private static int zoneRemove(CommandContext<CommandSourceStack> ctx) {
        int idx = IntegerArgumentType.getInteger(ctx, "index");
        if (!MoneyMod.zones().remove(idx)) return fail(ctx, "ไม่พบโซนหมายเลข " + idx + " (ดูด้วย /admin marketzone list)");
        ok(ctx, "§eลบโซนตลาด #" + idx + " แล้ว");
        return 1;
    }

    private static int adminOp(CommandContext<CommandSourceStack> ctx, String op) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        long amount = LongArgumentType.getLong(ctx, "amount");
        UUID id = target.getUUID();
        try {
            switch (op) {
                case "give" -> bank().adminGive(id, amount);
                case "take" -> bank().adminTake(id, amount);
                default -> bank().adminSet(id, amount);
            }
            ok(ctx, "§aยอดของ " + target.getName().getString() + " ตอนนี้ ฿" + bank().balance(id));
            return 1;
        } catch (BankException e) {
            return fail(ctx, e);
        }
    }
}
