package eu.mikart.wheelboat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

enum SurfaceMaterial {
    WATER,
    ICE,
    SAND,
    DIRT,
    STONE,
    WOOD,
    DEFAULT;

    static SurfaceMaterial under(final Entity entity) {
        Level level = entity.level();
        BlockPos boatPos = entity.blockPosition();

        SurfaceMaterial fluidMaterial = materialForFluid(level, boatPos);
        if (fluidMaterial != DEFAULT) {
            return fluidMaterial;
        }

        BlockPos groundPos = boatPos.below();
        fluidMaterial = materialForFluid(level, groundPos);
        if (fluidMaterial != DEFAULT) {
            return fluidMaterial;
        }

        return materialForBlock(level.getBlockState(groundPos));
    }

    private static SurfaceMaterial materialForFluid(Level level, BlockPos pos) {
        return level.getFluidState(pos).is(FluidTags.WATER) ? WATER : DEFAULT;
    }

    private static SurfaceMaterial materialForBlock(final @NotNull BlockState state) {
        var id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        String path = id.getPath().toLowerCase(Locale.ROOT);

        if (containsAny(path, "ice")) {
            return ICE;
        }
        if (containsAny(path, "sand", "gravel", "powder")) {
            return SAND;
        }
        if (containsAny(path, "dirt", "grass", "mud", "clay", "farmland", "podzol", "mycelium")) {
            return DIRT;
        }
        if (containsAny(path, "planks", "wood", "log", "stem", "hyphae")) {
            return WOOD;
        }
        if (!state.isAir() && isStoneLike(path, state.getBlock())) {
            return STONE;
        }
        return DEFAULT;
    }

    private static boolean isStoneLike(String path, Block block) {
        return containsAny(path, "stone", "deepslate", "granite", "diorite", "andesite", "basalt", "blackstone", "brick", "concrete", "terracotta")
            || block.defaultDestroyTime() >= 1.0F;
    }

    private static boolean containsAny(final @NotNull String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
