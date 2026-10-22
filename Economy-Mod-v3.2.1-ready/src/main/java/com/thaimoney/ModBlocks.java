package com.thaimoney;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class ModBlocks {
    public static final Block ATM;
    public static final BlockItem ATM_ITEM;

    static {
        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, MoneyMod.id("atm"));
        ATM = Registry.register(BuiltInRegistries.BLOCK, blockKey,
                new Block(BlockBehaviour.Properties.of().setId(blockKey).strength(3.5f, 6.0f).sound(SoundType.METAL)));

        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, MoneyMod.id("atm"));
        ATM_ITEM = Registry.register(BuiltInRegistries.ITEM, itemKey,
                new BlockItem(ATM, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
    }

    private ModBlocks() {}

    public static void init() {}
}
