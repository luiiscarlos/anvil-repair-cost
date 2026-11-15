package com.luis.anvilrepaircost.mixin;

import net.minecraft.world.inventory.AnvilMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin {
	private static final int PERCENT = 50;

	// 2.º almacenamiento de `tax` en createResult: la suma de REPAIR_COST de ambos objetos. Es lo único que se escala.
	@ModifyVariable(method = "createResult", at = @At(value = "STORE", ordinal = 1), ordinal = 0)
	private long anvilrepaircost$scalePenalty(long tax) {
		return (tax * PERCENT + 99L) / 100L;
	}
}
