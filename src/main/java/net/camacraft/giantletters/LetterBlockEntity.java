package net.camacraft.giantletters;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A letter's colour (white until dyed, saved and sent as a sign's is) and, on the client only, its place in the
 * bigger letter it is part of ({@link #tile()}), worked out from the blocks round it by {@link LetterGroups} and
 * kept until a letter block appears or disappears next to the region.
 */
public class LetterBlockEntity extends BlockEntity {
    private DyeColor color = DyeColor.WHITE;

    // client: the cached square and the cell's place in it
    private SquareCut.Tile tile = SquareCut.Tile.SINGLE;
    private boolean tileDirty = true;

    public LetterBlockEntity(BlockPos pos, BlockState state) {
        super(Letters.BLOCK_ENTITY.get(), pos, state);
    }

    public DyeColor color() {
        return color;
    }

    /** Sets the colour and tells the clients (the server's side of a dye). */
    public void setColor(DyeColor color) {
        if (this.color == color) return;
        this.color = color;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** Client: which square this block is in and where. Recomputed for the whole region when stale. */
    public SquareCut.Tile tile() {
        if (tileDirty && level != null) {
            LetterGroups.retile(level, worldPosition);
            if (tileDirty) {
                // the region did not include this block (its state changed under it): stand alone
                tile = SquareCut.Tile.SINGLE;
                tileDirty = false;
            }
        }
        return tile;
    }

    void setTile(SquareCut.Tile tile) {
        this.tile = tile;
        this.tileDirty = false;
    }

    void markTileDirty() {
        tileDirty = true;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide) LetterGroups.invalidateAround(level, worldPosition, getBlockState());
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && level.isClientSide) LetterGroups.invalidateAround(level, worldPosition, getBlockState());
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("Color", color.getName());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        color = DyeColor.byName(tag.getString("Color"), DyeColor.WHITE);
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
