package gaia.client.renderer;

import gaia.GrimoireOfGaia;
import gaia.client.ClientHandler;
import gaia.client.model.HunterModel;
import gaia.entity.Hunter;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;

public class HunterRenderer extends MobRenderer<Hunter, HunterModel> {
	public static final ResourceLocation[] HUNTER_LOCATIONS = new ResourceLocation[]{
			new ResourceLocation(GrimoireOfGaia.MOD_ID, "textures/entity/hunter/hunter.png")};

	public HunterRenderer(Context context) {
		super(context, new HunterModel(context.bakeLayer(ClientHandler.HUNTER)), ClientHandler.smallShadow);
		this.addLayer(new ItemInHandLayer<>(this));
	}

	@Override
	public ResourceLocation getTextureLocation(Hunter hunter) {
		return HUNTER_LOCATIONS[hunter.getVariant()];
	}
}