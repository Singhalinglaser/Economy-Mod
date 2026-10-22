package com.thaimoney;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.thaimoney.core.ShopPricing;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** แคตตาล็อกร้านระบบ (config/thaimoney_shop.json) แก้ราคาและสต็อกเป้าหมายได้เอง */
public final class ShopCatalog {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static List<ShopPricing.Entry> entries = new ArrayList<>();

    private ShopCatalog() {}

    public static List<ShopPricing.Entry> entries() {
        return entries;
    }

    private static List<ShopPricing.Entry> defaults() {
        List<ShopPricing.Entry> l = new ArrayList<>();
        String[][] d = {
            {"minecraft:dirt", "1", "6000"}, {"minecraft:cobblestone", "2", "6000"}, {"minecraft:sand", "2", "4000"},
            {"minecraft:gravel", "2", "4000"}, {"minecraft:oak_log", "4", "3000"}, {"minecraft:glass", "4", "2000"},
            {"minecraft:coal", "6", "2500"}, {"minecraft:string", "5", "1500"}, {"minecraft:bone", "3", "1500"},
            {"minecraft:leather", "8", "1200"}, {"minecraft:wheat", "3", "3000"}, {"minecraft:bread", "8", "1500"},
            {"minecraft:cooked_beef", "12", "1500"}, {"minecraft:redstone", "8", "2000"}, {"minecraft:lapis_lazuli", "10", "1500"},
            {"minecraft:iron_ingot", "25", "1500"}, {"minecraft:gold_ingot", "60", "800"}, {"minecraft:emerald", "90", "500"},
            {"minecraft:diamond", "400", "200"}, {"minecraft:blaze_rod", "80", "400"}, {"minecraft:ender_pearl", "150", "300"}
        };
        for (String[] r : d) l.add(new ShopPricing.Entry(r[0], Long.parseLong(r[1]), Integer.parseInt(r[2])));
        return l;
    }

    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("thaimoney_shop.json");
        try {
            if (Files.exists(path)) {
                List<ShopPricing.Entry> read = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8),
                        new TypeToken<List<ShopPricing.Entry>>() {}.getType());
                if (read != null) {
                    entries = new ArrayList<>(read);
                    return;
                }
            }
            entries = defaults();
            Files.writeString(path, GSON.toJson(entries), StandardCharsets.UTF_8);
        } catch (Exception e) {
            MoneyMod.LOGGER.error("Shop catalog error, using defaults", e);
            entries = defaults();
        }
    }

    /** แปลง id เป็นไอเทม คืน null ถ้าไม่มีในเกม */
    public static Item itemById(String id) {
        try {
            Identifier ident = Identifier.parse(id);
            Item item = BuiltInRegistries.ITEM.getValue(ident);
            return item == Items.AIR ? null : item;
        } catch (Exception e) {
            return null;
        }
    }
}
