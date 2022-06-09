package gaia.item.weapon.book;

import gaia.registry.GaiaSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class WitherBookItem extends WeaponBookItem {
	public WitherBookItem(Tier tier, Properties properties) {
		super(tier, properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> list, TooltipFlag flag) {
		super.appendHoverText(stack, level, list, flag);

		final Player player = net.minecraft.client.Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		if (player.getOffhandItem() == stack) {
			list.add(new TranslatableComponent("text.grimoireofgaia.bless.off_hand").withStyle(ChatFormatting.YELLOW));
		} else {
			list.add(new TranslatableComponent("text.grimoireofgaia.bless.main_hand").withStyle(ChatFormatting.YELLOW));
		}
		list.add(new TranslatableComponent(MobEffects.WITHER.getDescriptionId()).append(" II (0:04)"));
	}

	@Override
	public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		super.hurtEnemy(stack, target, attacker);

		attacker.level.playSound((Player) null, attacker.getX(), attacker.getY(), attacker.getZ(), GaiaSounds.BOOK_HIT.get(), SoundSource.NEUTRAL,
				1.0F, 1.0F);
		target.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 1));

		return true;
	}
}
