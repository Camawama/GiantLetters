package net.camacraft.giantletters;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.GlowInkSacItem;
import net.minecraft.world.item.InkSacItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * One letter on a wall, floor or ceiling: a one-pixel plate against the face it hangs on, nothing to collide with,
 * the glyph drawn by {@code client.LetterRenderer}. {@code FACE} and {@code FACING} say which way it reads
 * ({@link LetterFrame}); {@code single} (placed while sneaking) keeps it out of any bigger letter, as a sneak-placed
 * chest stays single; {@code glowing} is the glow ink, a block state so a glowing letter can give light.
 * The colour lives in the block entity. Dye, glow ink and an ink sac work as on a sign, on the whole letter.
 */
public class LetterBlock extends FaceAttachedHorizontalDirectionalBlock implements EntityBlock, SimpleWaterloggedBlock {
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final BooleanProperty SINGLE = BooleanProperty.create("single");
    public static final BooleanProperty GLOWING = BooleanProperty.create("glowing");

    private final Letters.Glyph glyph;

    public LetterBlock(Letters.Glyph glyph, Properties properties) {
        super(properties);
        this.glyph = glyph;
        registerDefaultState(stateDefinition.any()
                .setValue(FACE, AttachFace.WALL)
                .setValue(FACING, Direction.NORTH)
                .setValue(WATERLOGGED, false)
                .setValue(SINGLE, false)
                .setValue(GLOWING, false));
    }

    public Letters.Glyph glyph() {
        return glyph;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACE, FACING, WATERLOGGED, SINGLE, GLOWING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        if (state == null) return null;
        boolean water = context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER;
        return state.setValue(WATERLOGGED, water).setValue(SINGLE, context.isSecondaryUseActive());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return LetterFrame.of(state).plate();
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    /** A glowing letter gives the configured light ({@link Config#glowLightLevel()}), every block of a big one. */
    @Override
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(GLOWING) ? Config.glowLightLevel() : 0;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LetterBlockEntity(pos, state);
    }

    /**
     * The sign rules, on the whole letter this block is part of: a dye colours it (one dye, however big the letter),
     * glow ink makes it glow, an ink sac makes it black and takes the glow away. Nothing happens, and nothing is
     * used up, when the letter already is as asked.
     */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack stack = player.getItemInHand(hand);
        Item item = stack.getItem();
        DyeColor dye = item instanceof DyeItem d ? d.getDyeColor() : item instanceof InkSacItem ? DyeColor.BLACK : null;
        boolean glow = item instanceof GlowInkSacItem;
        boolean ink = item instanceof InkSacItem;
        if (dye == null && !glow) return InteractionResult.PASS;
        if (!player.getAbilities().mayBuild) return InteractionResult.PASS;

        List<BlockPos> letter = LetterGroups.square(level, pos);
        boolean changes = false;
        for (BlockPos p : letter) {
            BlockState s = level.getBlockState(p);
            if (!(s.getBlock() instanceof LetterBlock)) continue;
            if (dye != null && level.getBlockEntity(p) instanceof LetterBlockEntity be && be.color() != dye) changes = true;
            boolean wantGlow = glow || (s.getValue(GLOWING) && !ink);
            if (s.getValue(GLOWING) != wantGlow) changes = true;
        }
        if (!changes) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;

        for (BlockPos p : letter) {
            BlockState s = level.getBlockState(p);
            if (!(s.getBlock() instanceof LetterBlock)) continue;
            if (dye != null && level.getBlockEntity(p) instanceof LetterBlockEntity be) be.setColor(dye);
            boolean wantGlow = glow || (s.getValue(GLOWING) && !ink);
            if (s.getValue(GLOWING) != wantGlow) level.setBlock(p, s.setValue(GLOWING, wantGlow), Block.UPDATE_ALL);
        }
        SoundEvent sound = glow ? SoundEvents.GLOW_INK_SAC_USE : ink ? SoundEvents.INK_SAC_USE : SoundEvents.DYE_USE;
        level.playSound(null, pos, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
        if (!player.isCreative()) stack.shrink(1);
        player.awardStat(Stats.ITEM_USED.get(item));
        level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, state));
        return InteractionResult.CONSUME;
    }
}
