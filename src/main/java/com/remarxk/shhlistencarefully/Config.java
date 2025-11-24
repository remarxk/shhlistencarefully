package com.remarxk.shhlistencarefully;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.LongValue TIME = BUILDER
            .translation("config.shhlistencarefully.listen_time")
            .comment("Time required to enter listening")
            .defineInRange("listen_time",50, 0, 1000L);

    public static final ModConfigSpec.DoubleValue MAX_DISTANCE = BUILDER
            .translation("config.shhlistencarefully.max_distance")
            .comment("The farthest distance you can hear")
            .defineInRange("max_distance", 20d, 0d, 100d);

    public static final ModConfigSpec.IntValue MAX_NUM = BUILDER
            .translation("config.shhlistencarefully.max_num")
            .comment("The maximum number that can be listen")
            .defineInRange("max_num", 30, 0, 50);

    static final ModConfigSpec SPEC = BUILDER.build();
}
