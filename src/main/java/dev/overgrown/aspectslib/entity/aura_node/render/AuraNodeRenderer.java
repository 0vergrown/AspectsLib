package dev.overgrown.aspectslib.entity.aura_node.render;

import dev.overgrown.aspectslib.AspectsLib;
import dev.overgrown.aspectslib.entity.aura_node.client.AuraNodeVisibilityConfig;
import dev.overgrown.aspectslib.entity.aura_node.AuraNodeEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public class AuraNodeRenderer extends EntityRenderer<AuraNodeEntity> {

    private static final float SCALE = 1.0F;
    private static final float DEFAULT_ALPHA = 0.2f; // 20% opacity by default
    private static final float VISIBLE_ALPHA = 1.0f; // 100% opacity when visible

    public AuraNodeRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
    }

    @Override
    public Identifier getTexture(AuraNodeEntity entity) {
        // Return different texture based on node type
        return AspectsLib.identifier("textures/entity/aura_node/" +
                entity.getNodeType().name().toLowerCase() + ".png");
    }

    @Override
    public void render(AuraNodeEntity node, float yaw, float partialTick,
                       MatrixStack pose, VertexConsumerProvider bufferSource, int light) {
        super.render(node, yaw, partialTick, pose, bufferSource, light);

        MinecraftClient mc = MinecraftClient.getInstance();
        Camera camera = mc.gameRenderer.getCamera();

        // Check if it should show the node with full clarity
        boolean shouldShow = AuraNodeVisibilityConfig.shouldShowNode(mc.player, !node.getAspects().isEmpty());

        // Calculate alpha based on visibility condition
        float alpha = shouldShow ? VISIBLE_ALPHA : DEFAULT_ALPHA;

        // Don't render if completely transparent
        if (alpha <= 0.0f) {
            return;
        }

        pose.push();
        pose.multiply(camera.getRotation());

        VertexConsumer buffer = bufferSource.getBuffer(RenderLayer.getEntityTranslucent(getTexture(node)));

        int argb = node.getRenderColour();
        int baseAlpha = (argb >> 24) & 0xFF;
        int red = (argb >> 16) & 0xFF;
        int green = (argb >> 8) & 0xFF;
        int blue = argb & 0xFF;

        // Apply calculated alpha to the base color
        int finalAlpha = (int)(baseAlpha * alpha);
        if (finalAlpha <= 0) return; // Don't render if completely transparent

        Matrix4f pm = pose.peek().getPositionMatrix();
        Matrix3f nm = pose.peek().getNormalMatrix();

        vertex(buffer, pm, nm, 0.0F, 0, 0, 1, red, green, blue, finalAlpha);
        vertex(buffer, pm, nm, 1.0F, 0, 1, 1, red, green, blue, finalAlpha);
        vertex(buffer, pm, nm, 1.0F, 1, 1, 0, red, green, blue, finalAlpha);
        vertex(buffer, pm, nm, 0.0F, 1, 0, 0, red, green, blue, finalAlpha);

        pose.pop();
    }

    private static void vertex(VertexConsumer buffer, Matrix4f positionMatrix, Matrix3f normalMatrix, float x, int y, int u, int v, int r, int g, int b, int a) {
        buffer.vertex(positionMatrix, x - 0.5F, y - 0.25F, 0.0F)
                .color(r, g, b, a)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE)
                .normal(normalMatrix, 0.0F, 1.0F, 0.0F)
                .next();
    }
}