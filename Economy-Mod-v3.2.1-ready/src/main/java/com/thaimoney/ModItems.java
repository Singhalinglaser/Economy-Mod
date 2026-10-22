package com.thaimoney;

import com.thaimoney.core.Denomination;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

public final class ModItems {
    private static final Map<Denomination, CashItem> CASH = new EnumMap<>(Denomination.class);
    public static final Item WALLET;

    static {
        for (Denomination d : Denomination.values()) {
            ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MoneyMod.id(d.id));
            Item.Properties props = new Item.Properties().setId(key).stacksTo(d.maxStack);
            CashItem item = new CashItem(props, d);
            Registry.register(BuiltInRegistries.ITEM, key, item);
            CASH.put(d, item);
        }
        ResourceKey<Item> walletKey = ResourceKey.create(Registries.ITEM, MoneyMod.id("wallet"));
        WALLET = Registry.register(BuiltInRegistries.ITEM, walletKey,
                new Item(new Item.Properties().setId(walletKey).stacksTo(1)));
    }

    private ModItems() {}

    public static CashItem cash(Denomination d) {
        return CASH.get(d);
    }

    /** เรียกเพื่อให้ static block ทำงาน */
    public static void init() {}
}
