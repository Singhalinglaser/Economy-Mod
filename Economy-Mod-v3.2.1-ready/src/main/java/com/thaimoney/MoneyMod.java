package com.thaimoney;

import com.thaimoney.core.Auctions;
import com.thaimoney.core.Denomination;
import com.thaimoney.core.MarketZones;
import com.thaimoney.core.Bank;
import com.thaimoney.core.BankConfig;
import com.thaimoney.core.ChestShops;
import com.thaimoney.core.Mailbox;
import com.thaimoney.core.Market;
import com.thaimoney.core.PinStore;
import com.thaimoney.core.ShopPricing;
import com.thaimoney.core.Stalls;
import com.thaimoney.core.Wallets;
import com.thaimoney.gui.AtmMenu;
import com.thaimoney.gui.Gui;
import com.thaimoney.gui.WalletMenu;
import java.time.LocalDate;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MoneyMod implements ModInitializer {
    public static final String MOD_ID = "money";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static Bank bank;
    private static PinStore pins;
    private static Wallets wallets;
    private static Market market;
    private static ShopPricing pricing;
    private static Auctions auctions;
    private static Mailbox mailbox;
    private static Stalls stalls;
    private static ChestShops chestShops;
    private static MarketZones zones;
    private static BankStorage storage;
    private static MinecraftServer server;
    private static int tick;

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    public static Bank bank() { return bank; }
    public static PinStore pins() { return pins; }
    public static Wallets wallets() { return wallets; }
    public static Market market() { return market; }
    public static ShopPricing pricing() { return pricing; }
    public static Auctions auctions() { return auctions; }
    public static Mailbox mailbox() { return mailbox; }
    public static Stalls stalls() { return stalls; }
    public static ChestShops chestShops() { return chestShops; }
    public static MarketZones zones() { return zones; }

    /** ผู้เล่นยืนอยู่ในโซนตลาดหรือไม่ (ถ้าแอดมินยังไม่กำหนดโซน = ทุกที่ได้) */
    public static boolean inMarketZone(ServerPlayer p) {
        var pos = p.blockPosition();
        return zones.allows(p.level().dimension().toString(), pos.getX(), pos.getY(), pos.getZ());
    }

    public static final String ZONE_MESSAGE = "ต้องอยู่ในโซนตลาดเท่านั้น (ถามแอดมินว่าโซนตลาดอยู่ที่ไหน)";
    public static MinecraftServer server() { return server; }

    public static long today() {
        return LocalDate.now().toEpochDay();
    }

    /** ภาษีขายของผู้ขายคนนี้: ถ้าเช่าแผงตลาดอยู่ใช้อัตราแผง */
    public static int taxFor(UUID seller) {
        BankConfig cfg = bank.config();
        return stalls.active(seller, System.currentTimeMillis()) ? cfg.stallTaxBp : cfg.saleTaxBp;
    }

    public static int maxListingsFor(UUID seller) {
        BankConfig cfg = bank.config();
        return stalls.active(seller, System.currentTimeMillis()) ? cfg.stallMaxListings : cfg.maxListingsPerPlayer;
    }

    @Override
    public void onInitialize() {
        ModItems.init();
        ModBlocks.init();

        BankConfig cfg = BankStorage.loadConfig();
        bank = new Bank(cfg);
        pins = new PinStore(cfg.pinMaxAttempts, cfg.pinLockMinutes);
        wallets = new Wallets();
        market = new Market();
        pricing = new ShopPricing(cfg.shopSellRatioBp, cfg.shopRecoveryBpPerHour);
        auctions = new Auctions();
        mailbox = new Mailbox();
        stalls = new Stalls();
        chestShops = new ChestShops();
        zones = new MarketZones();
        ShopCatalog.load();

        // แท็บ Creative: เงินสดและกระเป๋าสตางค์ในแท็บ "เครื่องมือและยูทิลิตี้" ATM ในแท็บ "บล็อกใช้งาน"
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
            for (Denomination d : Denomination.values()) output.accept(ModItems.cash(d));
            output.accept(ModItems.WALLET);
        });
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output ->
                output.accept(ModBlocks.ATM_ITEM));

        ServerLifecycleEvents.SERVER_STARTED.register(s -> {
            server = s;
            storage = new BankStorage(s);
            storage.load();
            bank.setLogger(storage::log);
            LOGGER.info("ThaiMoney: loaded");
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
            if (storage != null) storage.save();
        });
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            tick++;
            if (storage == null) return;
            if (tick % 20 == 0) settleAuctions();   // ปิดประมูลที่หมดเวลา (ทุก 1 วินาที)
            if (tick % 1200 == 0) storage.save();   // บันทึกอัตโนมัติทุก 1 นาที
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                MoneyCommands.register(dispatcher));

        // ตู้ขายของ (ต้องมาก่อน ATM)
        UseBlockCallback.EVENT.register(ChestShopHandler::onUse);
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) ->
                ChestShopHandler.allowBreak(level, player, pos));

        // คลิกขวาที่ ATM → เปิดเมนู ATM
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            if (!level.getBlockState(hit.getBlockPos()).is(ModBlocks.ATM)) return InteractionResult.PASS;
            if (!level.isClientSide() && player instanceof ServerPlayer sp) AtmMenu.open(sp);
            return InteractionResult.SUCCESS;
        });

        // คลิกขวาถือกระเป๋าสตางค์ → เปิดกระเป๋า
        UseItemCallback.EVENT.register((player, level, hand) -> {
            ItemStack stack = player.getItemInHand(hand);
            if (!stack.is(ModItems.WALLET)) return InteractionResult.PASS;
            if (!level.isClientSide() && player instanceof ServerPlayer sp) WalletMenu.open(sp);
            return InteractionResult.SUCCESS;
        });
    }

    private static void settleAuctions() {
        BankConfig cfg = bank.config();
        for (Auctions.Settlement st : auctions.settleDue(bank, mailbox, System.currentTimeMillis(), cfg.auctionFeeBp)) {
            Auctions.Auction a = st.auction();
            ServerPlayer seller = server.getPlayerList().getPlayer(UUID.fromString(a.seller));
            if (!st.sold()) {
                if (seller != null) Gui.msg(seller, "§e[ประมูล] #" + a.id + " จบโดยไม่มีผู้เสนอราคา รับของคืนด้วย /claim");
                continue;
            }
            if (seller != null) {
                Gui.msg(seller, "§a[ประมูล] #" + a.id + " ขายได้ ฿" + a.currentBid + " (ค่าธรรมเนียม ฿" + st.fee() + ")");
            }
            ServerPlayer winner = server.getPlayerList().getPlayer(UUID.fromString(a.bidder));
            if (winner != null) Gui.msg(winner, "§a[ประมูล] คุณชนะ #" + a.id + " รับของด้วย /claim");
        }
    }
}
