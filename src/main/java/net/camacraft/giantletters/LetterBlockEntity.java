package net.camacraft.giantletters;

import net.camacraft.giantletters.client.LetterLightColors;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Nameable;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A letter's colour (white until dyed, saved and sent as a sign's is), the character it shows when it isn't the
 * block's own (a letter renamed in an anvil to one character: {@link #character()}, given back as the dropped
 * item's name through the loot table's {@code copy_name}), and, on the client only, its place in the bigger letter
 * it is part of ({@link #tile()}), worked out from the blocks round it by {@link LetterGroups} and kept until a
 * letter block appears or disappears next to the region. The client also tells Colorful Lighting the colour.
 */
public class LetterBlockEntity extends BlockEntity implements Nameable {
    private DyeColor color = DyeColor.WHITE;
    /** A custom character (a code point), or 0 for the block's own. */
    private int character;

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

    /** The character this letter shows: its custom one, or the block's own. */
    public int character() {
        if (character != 0) return character;
        return getBlockState().getBlock() instanceof LetterBlock block ? block.glyph().ch() : '?';
    }

    public boolean hasCustomCharacter() {
        return character != 0;
    }

    /** Sets a custom character (0 for the block's own) and tells the clients. */
    public void setCharacter(int codePoint) {
        if (this.character == codePoint) return;
        this.character = codePoint;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // ---- Nameable: a custom character is the letter's name, so the dropped item keeps it ----

    @Override
    public Component getName() {
        return hasCustomCharacter() ? characterName() : getBlockState().getBlock().getName();
    }

    @Override
    public boolean hasCustomName() {
        return hasCustomCharacter();
    }

    @Nullable
    @Override
    public Component getCustomName() {
        return hasCustomCharacter() ? characterName() : null;
    }

    private Component characterName() {
        return Component.literal(new String(Character.toChars(character)));
    }

    // ---- client: the square, the light colour ----

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
        if (level != null && level.isClientSide) {
            LetterGroups.invalidateAround(level, worldPosition, getBlockState());
            LetterLightColors.publish(this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && level.isClientSide) {
            LetterGroups.invalidateAround(level, worldPosition, getBlockState());
            LetterLightColors.forget(this);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("Color", color.getName());
        if (character != 0) tag.putInt("Char", character);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        color = DyeColor.byName(tag.getString("Color"), DyeColor.WHITE);
        int before = character;
        character = tag.contains("Char") ? tag.getInt("Char") : 0;
        if (level != null && level.isClientSide) {
            LetterLightColors.publish(this);
            // another character joins another region
            if (before != character) LetterGroups.invalidateAround(level, worldPosition, getBlockState());
        }
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
