package com.luis.anvilrepaircost;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import java.io.IOException;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class AnvilRepairCost implements ModInitializer {
	@Override
	public void onInitialize() {
		AnvilConfig.load();

		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) ->
			dispatcher.register(Commands.literal("anvilcost")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.executes(c -> {
					c.getSource().sendSuccess(() -> Component.literal("Penalización por trabajos previos: " + AnvilConfig.percent() + " %"), false);
					return AnvilConfig.percent();
				})
				.then(Commands.argument("percent", IntegerArgumentType.integer(0, 100))
					.executes(c -> {
						int value = IntegerArgumentType.getInteger(c, "percent");
						try {
							AnvilConfig.set(value);
						} catch (IOException e) {
							c.getSource().sendFailure(Component.literal("No se pudo guardar la configuración: " + e.getMessage()));
							return 0;
						}
						c.getSource().sendSuccess(() -> Component.literal("Penalización por trabajos previos: " + value + " %"), true);
						return value;
					}))));
	}
}
