package com.thaimoney;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.thaimoney.core.Auctions;
import com.thaimoney.core.BankConfig;
import com.thaimoney.core.ChestShops;
import com.thaimoney.core.Mailbox;
import com.thaimoney.core.MarketZones;
import com.thaimoney.core.Market;
import com.thaimoney.core.PinStore;
import com.thaimoney.core.ShopPricing;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/** เก็บสถานะทุกระบบเป็น JSON ในโฟลเดอร์โลก (ฝั่งเซิร์ฟเวอร์ล้วน) และบันทึกทุกธุรกรรมลง log */
public class BankStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** รูปแบบไฟล์ (ฟิลด์ใหม่ที่ไฟล์เก่าไม่มีจะเป็นค่าว่างตามค่าเริ่มต้น) */
    static class Data {
        Map<String, Long> balances = new HashMap<>();
        Map<String, long[]> daily = new HashMap<>();
        Map<String, List<String>> history = new HashMap<>();
        Map<String, PinStore.Record> pins = new HashMap<>();
        Map<String, Map<String, Long>> wallets = new HashMap<>();
        List<Market.Listing> listings = new ArrayList<>();
        long nextListingId = 1;
        Map<String, ShopPricing.Stock> shopStock = new HashMap<>();
        List<Auctions.Auction> auctions = new ArrayList<>();
        long nextAuctionId = 1;
        Map<String, List<Mailbox.Entry>> mailbox = new HashMap<>();
        Map<String, Long> stalls = new HashMap<>();
        List<ChestShops.Shop> chestShops = new ArrayList<>();
        List<MarketZones.Zone> zones = new ArrayList<>();
    }

    private final Path file;
    private final Path logFile;

    public BankStorage(MinecraftServer server) {
        Path dir = server.getWorldPath(LevelResource.ROOT).resolve("thaimoney");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            MoneyMod.LOGGER.error("Cannot create {}", dir, e);
        }
        this.file = dir.resolve("accounts.json");
        this.logFile = dir.resolve("transactions.log");
    }

    public static BankConfig loadConfig() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("thaimoney.json");
        try {
            if (Files.exists(path)) {
                BankConfig cfg = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), BankConfig.class);
                if (cfg != null) return cfg;
            }
            BankConfig def = new BankConfig();
            Files.writeString(path, GSON.toJson(def), StandardCharsets.UTF_8);
            return def;
        } catch (Exception e) {
            MoneyMod.LOGGER.error("Config error, using defaults", e);
            return new BankConfig();
        }
    }

    /** โหลดสถานะ ถ้าไม่มีไฟล์จะรีเซ็ตเป็นว่าง (กันข้อมูลโลกก่อนหน้าค้างตอนเล่นเดี่ยว) */
    public void load() {
        Data data = new Data();
        if (Files.exists(file)) {
            try {
                Data read = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Data.class);
                if (read != null) data = read;
            } catch (Exception e) {
                MoneyMod.LOGGER.error("Failed to read accounts file; NOT overwriting it, starting empty in memory", e);
                backupBroken();
            }
        }
        Map<UUID, Long> b = new HashMap<>();
        Map<UUID, long[]> d = new HashMap<>();
        Map<UUID, List<String>> h = new HashMap<>();
        Map<UUID, PinStore.Record> p = new HashMap<>();
        Map<UUID, Map<String, Long>> w = new HashMap<>();
        Map<UUID, List<Mailbox.Entry>> m = new HashMap<>();
        Map<UUID, Long> s = new HashMap<>();
        if (data.balances != null) data.balances.forEach((k, v) -> b.put(UUID.fromString(k), v));
        if (data.daily != null) data.daily.forEach((k, v) -> d.put(UUID.fromString(k), v));
        if (data.history != null) data.history.forEach((k, v) -> h.put(UUID.fromString(k), v));
        if (data.pins != null) data.pins.forEach((k, v) -> p.put(UUID.fromString(k), v));
        if (data.wallets != null) data.wallets.forEach((k, v) -> w.put(UUID.fromString(k), v));
        if (data.mailbox != null) data.mailbox.forEach((k, v) -> m.put(UUID.fromString(k), v));
        if (data.stalls != null) data.stalls.forEach((k, v) -> s.put(UUID.fromString(k), v));
        MoneyMod.bank().load(b, d);
        MoneyMod.bank().loadHistory(h);
        MoneyMod.pins().load(p);
        MoneyMod.wallets().load(w);
        MoneyMod.market().load(data.listings == null ? new ArrayList<>() : data.listings, data.nextListingId);
        MoneyMod.pricing().load(data.shopStock == null ? new HashMap<>() : data.shopStock);
        MoneyMod.auctions().load(data.auctions == null ? new ArrayList<>() : data.auctions, data.nextAuctionId);
        MoneyMod.mailbox().load(m);
        MoneyMod.stalls().load(s);
        MoneyMod.chestShops().load(data.chestShops == null ? new ArrayList<>() : data.chestShops);
        MoneyMod.zones().load(data.zones == null ? new ArrayList<>() : data.zones);
    }

    private void backupBroken() {
        try {
            Files.copy(file, file.resolveSibling("accounts.broken-" + System.currentTimeMillis() + ".json"));
        } catch (IOException ignored) {
            // ไม่เป็นไร
        }
    }

    public void save() {
        Data data = new Data();
        MoneyMod.bank().snapshotBalances().forEach((k, v) -> data.balances.put(k.toString(), v));
        MoneyMod.bank().snapshotDaily().forEach((k, v) -> data.daily.put(k.toString(), v));
        MoneyMod.bank().snapshotHistory().forEach((k, v) -> data.history.put(k.toString(), v));
        MoneyMod.pins().snapshot().forEach((k, v) -> data.pins.put(k.toString(), v));
        MoneyMod.wallets().snapshot().forEach((k, v) -> data.wallets.put(k.toString(), v));
        data.listings = MoneyMod.market().snapshot();
        data.nextListingId = MoneyMod.market().nextId();
        data.shopStock = MoneyMod.pricing().snapshot();
        data.auctions = MoneyMod.auctions().snapshot();
        data.nextAuctionId = MoneyMod.auctions().nextId();
        MoneyMod.mailbox().snapshot().forEach((k, v) -> data.mailbox.put(k.toString(), v));
        MoneyMod.stalls().snapshot().forEach((k, v) -> data.stalls.put(k.toString(), v));
        data.chestShops = MoneyMod.chestShops().snapshot();
        data.zones = MoneyMod.zones().snapshot();
        Path tmp = file.resolveSibling("accounts.json.tmp");
        try {
            Files.writeString(tmp, GSON.toJson(data), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            // Atomic replace when supported; fallback keeps the mod usable on filesystems without it.
            try {
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            MoneyMod.LOGGER.error("Failed to save accounts", e);
        }
    }

    public void log(String line) {
        try {
            Files.writeString(logFile, Instant.now() + " " + line + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            MoneyMod.LOGGER.error("Failed to write transaction log", e);
        }
    }
}
