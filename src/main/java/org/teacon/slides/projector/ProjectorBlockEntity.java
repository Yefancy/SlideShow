package org.teacon.slides.projector;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.teacon.slides.Registries;
import org.teacon.slides.renderer.ProjectorWorldRender;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

@SuppressWarnings("ConstantConditions")
@ParametersAreNonnullByDefault
public final class ProjectorBlockEntity extends BlockEntity implements MenuProvider {

    private static final Component TITLE = new TranslatableComponent("gui.slide_show.title");

    public String mLocation = "";
    public int mColor = ~0;
    public float mWidth = 1;
    public float mHeight = 1;
    public float mOffsetX = 0;
    public float mOffsetY = 0;
    public float mOffsetZ = 0;
    public boolean mDoubleSided = true;

    public ProjectorBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(Registries.BLOCK_ENTITY, blockPos, blockState);
    }

    public void openGui(BlockPos pos, Player player) {
        NetworkHooks.openGui((ServerPlayer) player, this, buf -> {
            buf.writeBlockPos(pos);
            CompoundTag tag = new CompoundTag();
            writeCustomTag(tag);
            buf.writeNbt(tag);
        });
    }

    @Nonnull
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new ProjectorContainerMenu(containerId, this);
    }

    @Nonnull
    @Override
    public Component getDisplayName() {
        return TITLE;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level.isClientSide) {
            ProjectorWorldRender.add(this);
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level.isClientSide) {
            ProjectorWorldRender.remove(this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level.isClientSide) {
            ProjectorWorldRender.remove(this);
        }
    }

    public void writeCustomTag(CompoundTag tag) {
        tag.putString("ImageLocation", mLocation);
        tag.putInt("Color", mColor);
        tag.putFloat("Width", mWidth);
        tag.putFloat("Height", mHeight);
        tag.putFloat("OffsetX", mOffsetX);
        tag.putFloat("OffsetY", mOffsetY);
        tag.putFloat("OffsetZ", mOffsetZ);
        tag.putBoolean("DoubleSided", mDoubleSided);
    }

    public void readCustomTag(CompoundTag tag) {
        mLocation = tag.getString("ImageLocation");
        mColor = tag.getInt("Color");
        mWidth = tag.getFloat("Width");
        mHeight = tag.getFloat("Height");
        mOffsetX = tag.getFloat("OffsetX");
        mOffsetY = tag.getFloat("OffsetY");
        mOffsetZ = tag.getFloat("OffsetZ");
        mDoubleSided = tag.getBoolean("DoubleSided");
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        writeCustomTag(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        readCustomTag(tag);
    }

    @Nonnull
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet) {
        readCustomTag(packet.getTag());
    }

    @Nonnull
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        writeCustomTag(tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        load(tag);
    }

    @Override
    public AABB getRenderBoundingBox() {
        float fromOffsetX = 0;
        float fromOffsetY = 0;
        float fromOffsetZ = 0;
        float widthX = 1;
        float widthY = 1;
        float widthZ = 1;

        ProjectorBlock.InternalRotation rotation = getBlockState().getValue(ProjectorBlock.ROTATION);

        Direction facing = getBlockState().getValue(BlockStateProperties.FACING);
        fromOffsetX += facing.getStepX() * (mOffsetZ + 0.5);
        fromOffsetY += facing.getStepY() * (mOffsetZ + 0.5);
        fromOffsetZ += facing.getStepZ() * (mOffsetZ + 0.5);

        if (facing.getAxis() == Direction.Axis.Y) {
            if (rotation == ProjectorBlock.InternalRotation.HORIZONTAL_FLIPPED) {
                fromOffsetX -= mOffsetX + (mWidth - 1) / 2;
                fromOffsetZ += mOffsetY - (mHeight - 1) / 2;
                widthX = mWidth;
                widthZ = mHeight;
            } else if (rotation == ProjectorBlock.InternalRotation.DIAGONAL_FLIPPED) {
                fromOffsetX -= mOffsetY - (mHeight - 1) / 2;
                fromOffsetZ -= mOffsetX + (mWidth - 1) / 2;
                widthX = mHeight;
                widthZ = mWidth;
            } else if (rotation == ProjectorBlock.InternalRotation.VERTICAL_FLIPPED) {
                fromOffsetX += mOffsetX + (mWidth - 1) / 2;
                fromOffsetZ -= mOffsetY - (mHeight - 1) / 2;
                widthX = mWidth;
                widthZ = mHeight;
            } else if (rotation == ProjectorBlock.InternalRotation.ANTI_DIAGONAL_FLIPPED) {
                fromOffsetX += mOffsetY - (mHeight - 1) / 2;
                fromOffsetZ += mOffsetX + (mWidth - 1) / 2;
                widthX = mHeight;
                widthZ = mWidth;
            }
            if (facing == Direction.DOWN) {
                fromOffsetZ = -fromOffsetZ;
            }
        } else {
            widthY = mHeight;
            fromOffsetY += (mHeight - 1) / 2 - mOffsetY;
            if (facing.getAxis() == Direction.Axis.X) {
                widthZ = mWidth;
                fromOffsetZ += facing.getStepX() * ((mWidth - 1) / 2 + mOffsetX);
            } else {
                widthX = mWidth;
                fromOffsetX -= facing.getStepZ() * ((mWidth - 1) / 2 + mOffsetX);
            }
        }

        widthX /= 2;
        widthZ /= 2;
        widthY /= 2;

        return new AABB(Vec3.atCenterOf(worldPosition).add(fromOffsetX + widthX, fromOffsetY + widthY, fromOffsetZ + widthZ),
                Vec3.atCenterOf(worldPosition).add(fromOffsetX - widthX, fromOffsetY - widthY, fromOffsetZ - widthZ));
    }
}
