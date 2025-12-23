package euphy.upo.sentrymechanicalarm.content;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import euphy.upo.sentrymechanicalarm.registry.SentryPartialModels;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class BlazeFireControlRenderer extends SafeBlockEntityRenderer<BlazeFireControlBlockEntity> {

    public BlazeFireControlRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected void renderSafe(BlazeFireControlBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null) return;

        float renderTime = AnimationTickHolder.getRenderTime(level);

        float animation = be.headAnimation.getValue(partialTicks) * .175f;
        float horizontalAngle = AngleHelper.rad(be.headAngle.getValue(partialTicks));

        int seed = be.hashCode();
        float seededRenderTime = renderTime + (seed % 13) * 16f;
        float offset = Mth.sin((seededRenderTime / 16f) % (2 * Mth.PI)) / 16f; 
        float headY = offset - (animation * .75f); 

        ms.pushPose();
 
        PartialModel headModel = SentryPartialModels.BLAZE_FIRE_CONTROLLER_HEAD;
        SuperByteBuffer headBuffer = CachedBuffers.partial(headModel, be.getBlockState());

        headBuffer.translate(0, headY, 0)
                .rotateCentered(horizontalAngle, Direction.UP)
                .light(LightTexture.FULL_BRIGHT)
                .renderInto(ms, buffer.getBuffer(RenderType.solid()));

        PartialModel ringModel = SentryPartialModels.RING;
        SuperByteBuffer ringBuffer = CachedBuffers.partial(ringModel, be.getBlockState());

 
        ringBuffer.translate(0, headY, 0)
                .rotateCentered(horizontalAngle, Direction.UP)
                .light(LightTexture.FULL_BRIGHT)
                .renderInto(ms, buffer.getBuffer(RenderType.cutout()));

        ItemStack clipboardStack = be.inventory.getStackInSlot(0);
        if (!clipboardStack.isEmpty()) {

            float scale = 0.5f;
            float offsetX = 0.3f;
            float offsetY = 0.3f;
            float offsetZ = 0.15f;
            float rotX = 65.0f; 
            float rotY = 180.0f;
            float rotZ = 35.0f;

            PartialModel clipboardModel = SentryPartialModels.CLIPBOARD; 
            SuperByteBuffer clipboardBuffer = CachedBuffers.partial(clipboardModel, be.getBlockState());

            clipboardBuffer
                    .translate(0, headY, 0)
                    .rotateCentered(horizontalAngle, Direction.UP)
                    .translate(offsetX, offsetY, offsetZ)
                    .rotate(AngleHelper.rad(rotX), Direction.Axis.X)
                    .rotate(AngleHelper.rad(rotY), Direction.Axis.Y)
                    .rotate(AngleHelper.rad(rotZ), Direction.Axis.Z)
                    .scale(scale)
                    .light(LightTexture.FULL_BRIGHT) 
                    .renderInto(ms, buffer.getBuffer(RenderType.cutout())); 
        }
        ms.popPose();

    }
}