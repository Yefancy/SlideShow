package org.teacon.slides.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Matrix4f;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import org.teacon.slides.projector.ProjectorBlock;
import org.teacon.slides.projector.ProjectorBlockEntity;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public class ProjectorRenderer implements BlockEntityRenderer<ProjectorBlockEntity> {

    public static final ProjectorRenderer INSTANCE = new ProjectorRenderer();

    private ProjectorRenderer() {
    }

    @Nonnull
    public ProjectorRenderer onCreate(@SuppressWarnings("unused") @Nonnull BlockEntityRendererProvider.Context context) {
        return this;
    }

    @Override
    public void render(ProjectorBlockEntity tile, float partialTick, PoseStack pStack,
                       MultiBufferSource source, int packedLight, int packedOverlay) {

        // render bounding box for DEBUG
//        renderBoundingBox(pStack, tile);

        // always update slide state
        final Slide slide = SlideState.getSlide(tile.mLocation);
        if (slide == null) {
            return;
        }
        if (!tile.getBlockState().getValue(BlockStateProperties.POWERED)) {
            int color = tile.mColor;
            if ((color & 0xFF000000) == 0) {
                return;
            }

            pStack.pushPose();

            PoseStack.Pose last = pStack.last();
            tile.transformToSlideSpace(last.pose(), last.normal());

            final boolean flipped = tile.getBlockState().getValue(ProjectorBlock.ROTATION).isFlipped();

            slide.render(source, last.pose(), last.normal(), tile.mWidth, tile.mHeight, color, LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, flipped || tile.mDoubleSided, !flipped || tile.mDoubleSided,
                    SlideState.getAnimationTick(), partialTick);

            pStack.popPose();
        }
    }

    @Override
    public boolean shouldRenderOffScreen(ProjectorBlockEntity tile) {
        // global rendering
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }

    public static void renderBoundingBox(PoseStack matrixStack, ProjectorBlockEntity tile) {
        AABB box = tile.getRenderBoundingBox();
        BlockPos pos = tile.getBlockPos();
        Tesselator tessellator = Tesselator.getInstance();
        BufferBuilder buffer = tessellator.getBuilder();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        matrixStack.pushPose();

        matrixStack.translate(-pos.getX(), -pos.getY(), -pos.getZ());

        float minX = (float) box.minX;
        float minY = (float) box.minY;
        float minZ = (float) box.minZ;
        float maxX = (float) box.maxX;
        float maxY = (float) box.maxY;
        float maxZ = (float) box.maxZ;
        
        Matrix4f mat = matrixStack.last().pose();
        float b = 1;
        buffer.vertex(mat, minX, minY, minZ).color(0, 0, b, 0.5f).endVertex();
        buffer.vertex(mat, minX, minY, maxZ).color(0, 0, b, 0.5f).endVertex();
        buffer.vertex(mat, minX, maxY, maxZ).color(0, 0, b, 0.5f).endVertex();
        buffer.vertex(mat, minX, maxY, minZ).color(0, 0, b, 0.5f).endVertex();

        buffer.vertex(mat, maxX, minY, minZ).color(0, 0, b, 0.5f).endVertex();
        buffer.vertex(mat, maxX, maxY, minZ).color(0, 0, b, 0.5f).endVertex();
        buffer.vertex(mat, maxX, maxY, maxZ).color(0, 0, b, 0.5f).endVertex();
        buffer.vertex(mat, maxX, minY, maxZ).color(0, 0, b, 0.5f).endVertex();

        b = 0.5f;
        buffer.vertex(mat, minX, minY, minZ).color(0, 0, b, 0.5f).endVertex();
        buffer.vertex(mat, maxX, minY, minZ).color(0, 0, b, 0.5f).endVertex();
        buffer.vertex(mat, maxX, minY, maxZ).color(0, 0, b, 0.5f).endVertex();
        buffer.vertex(mat, minX, minY, maxZ).color(0, 0, b, 0.5f).endVertex();

        b = 1;
        buffer.vertex(mat, minX, maxY, minZ).color(0, 0, b, 0.5f).endVertex();
        buffer.vertex(mat, minX, maxY, maxZ).color(0, 0, b, 0.5f).endVertex();
        buffer.vertex(mat, maxX, maxY, maxZ).color(0, 0, b, 0.5f).endVertex();
        buffer.vertex(mat, maxX, maxY, minZ).color(0, 0, b, 0.5f).endVertex();

        b = 0.8f;
        buffer.vertex(mat, minX, minY, minZ).color(0, 0, b, 0.5f).endVertex();
        buffer.vertex(mat, minX, maxY, minZ).color(0, 0, b, 0.5f).endVertex();
        buffer.vertex(mat, maxX, maxY, minZ).color(0, 0, b, 0.5f).endVertex();
        buffer.vertex(mat, maxX, minY, minZ).color(0, 0, b, 0.5f).endVertex();

        buffer.vertex(mat, minX, minY, maxZ).color(0, 0, b, 0.5f).endVertex();
        buffer.vertex(mat, maxX, minY, maxZ).color(0, 0, b, 0.5f).endVertex();
        buffer.vertex(mat, maxX, maxY, maxZ).color(0, 0, b, 0.5f).endVertex();
        buffer.vertex(mat, minX, maxY, maxZ).color(0, 0, b, 0.5f).endVertex();

        tessellator.end();
        RenderSystem.depthMask(true);
        matrixStack.popPose();
    }
}
