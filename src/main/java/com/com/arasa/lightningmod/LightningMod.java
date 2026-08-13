package com.arasa.lightningmod;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class LightningMod implements ModInitializer {

    private static final String ALLOWED_USER = "kasssar7m";
    private final Map<UUID, ScheduledFuture<?>> autoTasks = new HashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("simsek")
                .requires(source -> isAllowedUser(source))
                // Tekli Şimşek: /simsek <oyuncu>
                .then(argument("hedef", EntityArgumentType.player())
                    .executes(context -> {
                        ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "hedef");
                        strikeLightning(target);
                        context.getSource().sendFeedback(() -> Text.literal(target.getName().getString() + " adlı oyuncuya şimşek çaktırıldı!").formatted(Formatting.GREEN), false);
                        return 1;
                    })
                )
                // Otomatik Şimşek: /simsek oto <oyuncu>
                .then(literal("oto")
                    .then(argument("hedef", EntityArgumentType.player())
                        .executes(context -> {
                            ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "hedef");
                            UUID targetId = target.getUuid();

                            if (autoTasks.containsKey(targetId)) {
                                autoTasks.get(targetId).cancel(true);
                                autoTasks.remove(targetId);
                                context.getSource().sendFeedback(() -> Text.literal(target.getName().getString() + " için otomatik şimşek DURDURULDU.").formatted(Formatting.GREEN), false);
                            } else {
                                ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(() -> {
                                    if (target.isAlive() && target.networkHandler.isConnected()) {
                                        target.getServer().execute(() -> strikeLightning(target));
                                    } else {
                                        if (autoTasks.containsKey(targetId)) {
                                            autoTasks.get(targetId).cancel(true);
                                            autoTasks.remove(targetId);
                                        }
                                    }
                                }, 0, 1, TimeUnit.SECONDS);

                                autoTasks.put(targetId, task);
                                context.getSource().sendFeedback(() -> Text.literal(target.getName().getString() + " için otomatik şimşek BAŞLATILDI!").formatted(Formatting.RED), false);
                            }
                            return 1;
                        })
                    )
                )
            );
        });
    }

    private boolean isAllowedUser(ServerCommandSource source) {
        if (source.getEntity() instanceof ServerPlayerEntity player) {
            return player.getName().getString().equalsIgnoreCase(ALLOWED_USER);
        }
        return false;
    }

    private void strikeLightning(ServerPlayerEntity target) {
        LightningEntity lightning = EntityType.LIGHTNING_BOLT.create(target.getWorld());
        if (lightning != null) {
            lightning.refreshPositionAfterTeleport(target.getX(), target.getY(), target.getZ());
            target.getWorld().spawnEntity(lightning);
        }
    }
}