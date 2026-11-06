package com.riftwatcher;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Test commands so you can meet the Watcher without waiting for the dark to do its work.
 * Switch them off with "debugCommands": false in config/riftwatcher.json (do that on public servers).
 *
 *   /riftwatcher status          shows your Dread and stage
 *   /riftwatcher dread <0-1000>  sets your Dread (350 = it starts watching, 650 = it hunts)
 *   /riftwatcher summon          opens a rift close by; the Watcher hunts you (ignores light)
 *   /riftwatcher summon watch    same, but it only stands and stares
 *   /riftwatcher banish          removes every Watcher
 */
public final class WatcherCommand {
    private WatcherCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("riftwatcher")
                .then(Commands.literal("status").executes(WatcherCommand::status))
                .then(Commands.literal("dread")
                        .then(Commands.argument("amount", IntegerArgumentType.integer(0, 1000))
                                .executes(WatcherCommand::setDread)))
                .then(Commands.literal("summon")
                        .executes(ctx -> summon(ctx, 3))
                        .then(Commands.literal("watch").executes(ctx -> summon(ctx, 2))))
                .then(Commands.literal("banish").executes(WatcherCommand::banish)));
    }

    private static boolean enabled(CommandContext<CommandSourceStack> ctx) {
        if (RiftConfig.get().debugCommands) {
            return true;
        }
        ctx.getSource().sendFailure(Component.literal("Rift Watcher commands are disabled in config/riftwatcher.json"));
        return false;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!enabled(ctx)) {
            return 0;
        }
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        int dread = Haunting.getDread(player);
        String message = "Dread " + dread + "/1000, stage " + Haunting.stageOf(dread);
        ctx.getSource().sendSuccess(() -> Component.literal(message), false);
        return dread;
    }

    private static int setDread(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!enabled(ctx)) {
            return 0;
        }
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        Haunting.setDread(player, amount);
        String message = "Dread set to " + amount + " (stage " + Haunting.stageOf(amount) + ")";
        ctx.getSource().sendSuccess(() -> Component.literal(message), false);
        return amount;
    }

    private static int summon(CommandContext<CommandSourceStack> ctx, int stage) throws CommandSyntaxException {
        if (!enabled(ctx)) {
            return 0;
        }
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = (ServerLevel) player.level();
        if (Haunting.summon(level, player, true, stage)) {
            ctx.getSource().sendSuccess(() -> Component.literal("A rift opens."), false);
            return 1;
        }
        ctx.getSource().sendFailure(Component.literal("No room for a rift here. Try somewhere with open ground."));
        return 0;
    }

    private static int banish(CommandContext<CommandSourceStack> ctx) {
        if (!enabled(ctx)) {
            return 0;
        }
        int removed = Haunting.banishAll(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.literal("Closed the rifts. Removed " + removed + " Watcher(s)."), false);
        return removed;
    }
}
