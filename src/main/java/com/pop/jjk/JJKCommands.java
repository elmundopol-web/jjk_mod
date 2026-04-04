package com.pop.jjk;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class JJKCommands {

    private JJKCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(Commands.literal("jjk")
                .then(Commands.literal("nocooldown")
                    .executes(context -> toggleNoCooldown(context.getSource())))
                .then(Commands.literal("clearcooldown")
                    .executes(context -> clearCooldown(context.getSource())))
                .then(Commands.literal("infiniteenergy")
                    .executes(context -> toggleInfiniteEnergy(context.getSource()))))
        );
    }

    private static int toggleNoCooldown(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        boolean enabled = BlueTechniqueHandler.toggleNoCooldown(player);

        if (enabled) {
            InfiniteDomainTechniqueHandler.clearCooldown(player);
            MalevolentShrineTechniqueHandler.clearCooldown(player);
            PiercingBloodTechniqueHandler.clearCooldown(player);
            FlowingRedScaleTechniqueHandler.clearCooldown(player);
            SupernovaTechniqueHandler.clearCooldown(player);
            source.sendSuccess(() -> Component.literal("Cooldowns de Gojo desactivados para ti."), false);
        } else {
            source.sendSuccess(() -> Component.literal("Cooldowns de Gojo reactivados."), false);
        }

        return 1;
    }

    private static int clearCooldown(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        BlueTechniqueHandler.clearCooldown(player);
        InfiniteDomainTechniqueHandler.clearCooldown(player);
        MalevolentShrineTechniqueHandler.clearCooldown(player);
        PiercingBloodTechniqueHandler.clearCooldown(player);
        FlowingRedScaleTechniqueHandler.clearCooldown(player);
        SupernovaTechniqueHandler.clearCooldown(player);
        source.sendSuccess(() -> Component.literal("Cooldowns de Gojo limpiados."), false);
        return 1;
    }

    private static int toggleInfiniteEnergy(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        boolean enabled = CursedEnergyManager.toggleInfiniteEnergy(player);

        if (enabled) {
            source.sendSuccess(() -> Component.literal("Energia maldita infinita activada."), false);
        } else {
            source.sendSuccess(() -> Component.literal("Energia maldita infinita desactivada."), false);
        }

        return 1;
    }
}
