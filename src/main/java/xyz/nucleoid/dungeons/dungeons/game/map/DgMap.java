package xyz.nucleoid.dungeons.dungeons.game.map;

import com.mojang.datafixers.util.Either;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;

import net.minecraft.text.LiteralText;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import xyz.nucleoid.dungeons.dungeons.game.DgConfig;
import xyz.nucleoid.dungeons.dungeons.game.map.gen.DgChunkGenerator;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import xyz.nucleoid.dungeons.dungeons.game.map.gen.DgProcgenMapConfig;
import xyz.nucleoid.plasmid.game.GameOpenException;
import xyz.nucleoid.plasmid.map.template.*;
import xyz.nucleoid.plasmid.util.BlockBounds;

import java.io.IOException;
import java.util.Optional;

public class DgMap {
    public final BlockBounds spawn;
    public final float spawnAngle;
    public final Either<MapTemplate, DgProcgenMapConfig> templateOrGenerator;

    private DgMap(BlockBounds spawn, float spawnAngle, Either<MapTemplate, DgProcgenMapConfig> templateOrGenerator) {
        this.spawn = spawn;
        this.spawnAngle = spawnAngle;
        this.templateOrGenerator = templateOrGenerator;
    }

    public static DgMap create(DgConfig config) throws GameOpenException {
        Either<MapTemplate, DgProcgenMapConfig> either = config.map.mapLeft(id -> {
            try {
                return MapTemplateSerializer.INSTANCE.loadFromResource(id);
            } catch (IOException e) {
                throw new GameOpenException(new LiteralText("Failed to load map"), e);
            }
        });

        Optional<DgMap> opt = either.left().map(DgMap::fromTemplate);

        if (!opt.isPresent()) {
            opt = either.right().map(DgMap::fromGenerator);
            assert opt.isPresent();
        }
        return opt.get();
    }

    private static DgMap fromTemplate(MapTemplate template) throws GameOpenException {
        MapTemplateMetadata meta = template.getMetadata();
        CompoundTag globalData = meta.getData();

        RegistryKey<Biome> biome = BiomeKeys.FOREST;
        if (globalData.contains("biome")) {
            biome = RegistryKey.of(Registry.BIOME_KEY, new Identifier(globalData.getString("biome")));
        }
        template.setBiome(biome);

        TemplateRegion spawnRegion = meta.getFirstRegion("spawn");
        if (spawnRegion == null) {
            throw new GameOpenException(new LiteralText("No `spawn` region is present but it is required"));
        }

        return new DgMap(spawnRegion.getBounds(), spawnRegion.getData().getFloat("yaw"), Either.left(template));
    }

    private static DgMap fromGenerator(DgProcgenMapConfig config) {
        return new DgMap(BlockBounds.of(new BlockPos(0, 40, 0)), 0, Either.right(config));
    }

    public ChunkGenerator asGenerator(MinecraftServer server) {
        Optional<ChunkGenerator> opt = this.templateOrGenerator.left().map(t -> new TemplateChunkGenerator(server, t));

        if (!opt.isPresent()) {
            opt = this.templateOrGenerator.right().map(cfg -> new DgChunkGenerator(server));
            assert opt.isPresent();
        }
        return opt.get();
    }
}
