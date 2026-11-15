package com.luis.anvilrepaircost.mixin;

import com.luis.anvilrepaircost.AnvilConfig;
import net.minecraft.world.inventory.AnvilMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin {
	// 2.º almacenamiento de `tax` en createResult: la suma de REPAIR_COST de ambos objetos. Es lo único que se escala.
	@ModifyVariable(method = "createResult", at = @At(value = "STORE", ordinal = 1), ordinal = 0)
	private long anvilrepaircost$scalePenalty(long tax) {
		return (tax * AnvilConfig.percent() + 99L) / 100L;
	}
}
