package greektrees;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import bcf.guidebook.Chapter;
import bcf.guidebook.Section;

import greektrees.tree.AriesOak;
import greektrees.tree.Drafts;
import greektrees.tree.Shape;
import greektrees.tree.TreeShapes;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Dev-only check, active with {@code -Dgreektrees.selftest=<growths per tree>} ({@code ./gradlew runServer
 * -PselfTest}): grows every sapling on platforms high above spawn through the vanilla sapling path, lets vanilla
 * recompute every leaf's distance and run its decay tick, checks blocked growth, sand placement, the recipes, the
 * fruit, the olive pit and the creative tab, writes run/greektrees-selftest/ (summary.txt and the first twelve trees
 * per species as JSON) and stops the server.
 */
final class DevSelfTest {
    private static final String PROPERTY = "greektrees.selftest";
    private static final int Y = 200;
    /** Same ingredients as data/greektrees/recipe, listed in reverse to show the order does not matter. */
    private static final Map<String, List<Item>> RECIPES = Map.of(
            "cypress", List.of(Items.ACACIA_SAPLING, Items.SPRUCE_SAPLING),
            "olive", List.of(Items.APPLE, Items.AZALEA_LEAVES, Items.OAK_SAPLING),
            "fig", List.of(Items.SWEET_BERRIES, Items.ACACIA_SAPLING, Items.ACACIA_SAPLING),
            "strawberry_tree", List.of(Items.SWEET_BERRIES, Items.ACACIA_SAPLING),
            "date_palm", List.of(Items.COCOA_BEANS, Items.JUNGLE_SAPLING),
            "mulberry", List.of(Items.SWEET_BERRIES, Items.BIRCH_SAPLING),
            "weeping_willow", List.of(Items.BIRCH_SAPLING, Items.AZALEA),
            "aries_oak", List.of(Items.GLOW_BERRIES, Items.FLOWERING_AZALEA, Items.DARK_OAK_SAPLING));
    /** The fruit blocks a tree may carry when it has grown, one of them per tree (the weeping willow carries none). */
    private static final Map<String, List<Block>> FRUIT = Map.of("olive", List.of(GreekTrees.OLIVE_TWIG),
            "fig", List.of(GreekTrees.FIG_TWIG), "strawberry_tree", List.of(GreekTrees.ARBUTUS_TWIG),
            "date_palm", List.of(GreekTrees.DATE_CLUSTER),
            "mulberry", List.of(GreekTrees.MULBERRY_TWIGS));

    private DevSelfTest() {
    }

    static void register() {
        String drafts = System.getProperty("greektrees.drafts");
        if (drafts != null) {
            drafts(drafts);
        }
        if (System.getProperty(PROPERTY) == null) {
            return;
        }
        ServerLifecycleEvents.SERVER_STARTED.register(DevSelfTest::run);
    }

    /**
     * {@code ./gradlew runServer -Pdrafts=p1,w1}: rolls three trees for every variant of the given rounds of
     * {@link Drafts} (the same three seeds for each variant), writes them to run/greektrees-drafts/ for
     * concept/render_selftest.py and exits before the server loads a world.
     */
    private static void drafts(String rounds) {
        Path out = FabricLoader.getInstance().getGameDir().resolve("greektrees-drafts");
        try {
            Files.createDirectories(out);
            for (String round : rounds.split(",")) {
                try (var old = Files.newDirectoryStream(out, "draft_" + round + "_*.json")) {
                    for (Path p : old) {
                        Files.delete(p);
                    }
                }
                RandomSource master = RandomSource.create(round.hashCode()); // seeds in a row start alike
                long[] seeds = {master.nextLong(), master.nextLong(), master.nextLong()};
                for (var variant : Drafts.round(round).entrySet()) {
                    for (int n = 0; n < seeds.length; n++) {
                        StringBuilder json = new StringBuilder("[\n");
                        variant.getValue().apply(RandomSource.create(seeds[n]), n).cells()
                                .forEach((p, c) -> jsonLine(json, p.getX(), p.getY(), p.getZ(), c.state()));
                        Files.writeString(out.resolve("draft_" + round + "_" + variant.getKey() + "_" + n + ".json"),
                                json.append("\n]\n"), StandardCharsets.UTF_8);
                    }
                }
                System.out.println("[greektrees-drafts] wrote round " + round);
            }
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        System.exit(0);
    }

    private static void run(MinecraftServer server) {
        int growths = Integer.getInteger(PROPERTY, 12);
        ServerLevel level = server.overworld();
        RandomSource random = level.getRandom();
        Path out = FabricLoader.getInstance().getGameDir().resolve("greektrees-selftest");
        List<String> lines = new ArrayList<>();
        int failures = 0;
        // other mods dropped into run/mods (e.g. a harvest mod) take part in the right-click checks
        List<String> others = FabricLoader.getInstance().getAllMods().stream().map(m -> m.getMetadata().getId())
                .filter(id -> !id.startsWith("fabric") && !List.of("greektrees", "bcfguidebooklib", "java", "minecraft", "mixinextras")
                        .contains(id)).sorted().toList();
        lines.add("other mods loaded: " + (others.isEmpty() ? "none" : String.join(", ", others)));
        try {
            Files.createDirectories(out);
            try (var old = Files.newDirectoryStream(out, "*.json")) { // species grow different numbers of trees
                for (Path p : old) {
                    Files.delete(p);
                }
            }
            for (int s = 0; s < GreekTrees.SPECIES.size(); s++) {
                GreekTrees.Species species = GreekTrees.SPECIES.get(s);
                if (species.sapling() instanceof AriesOakSaplingBlock) {
                    failures += ariesOak(level, random, out, lines);
                    failures += plantAndCraft(server, level, species, s, lines);
                    continue;
                }
                int grown = 0, minH = 99, maxH = 0, minW = 99, maxW = 0, logs = 0, leaves = 0, mismatched = 0, decayed = 0;
                int fruit = 0, minFruit = 99, maxFruit = 0, looseFruit = 0, mixedTrees = 0, bare = 0;
                int mixedKinds = 0, vines = 0, looseVines = 0;
                int[] stages = new int[3];
                List<Block> fruitBlocks = FRUIT.getOrDefault(species.name(), List.of());
                boolean palm = species.name().contains("date_palm");
                for (int i = 0; i < growths; i++) {
                    BlockPos pos = new BlockPos(s * 48, Y, i * 40);
                    platform(level, pos, Blocks.GRASS_BLOCK);
                    level.setBlockAndUpdate(pos, species.sapling().defaultBlockState());
                    grow(level, pos, random);
                    Scan scan = scan(level, pos);
                    if (!level.getBlockState(pos).is(BlockTags.LOGS)) {
                        lines.add(species.name() + " growth " + i + ": did not grow");
                        failures++;
                        continue;
                    }
                    grown++;
                    minH = Math.min(minH, scan.height);
                    maxH = Math.max(maxH, scan.height);
                    minW = Math.min(minW, scan.width);
                    maxW = Math.max(maxW, scan.width);
                    logs += scan.logs;
                    leaves += scan.leaves.size();
                    fruit += scan.fruit.size();
                    minFruit = Math.min(minFruit, scan.fruit.size());
                    maxFruit = Math.max(maxFruit, scan.fruit.size());
                    Set<Integer> treeStages = new HashSet<>();
                    Set<Block> treeKinds = new HashSet<>();
                    for (BlockPos p : scan.fruit) {
                        int a = age(level.getBlockState(p));
                        stages[a]++;
                        treeStages.add(a);
                        treeKinds.add(level.getBlockState(p).getBlock());
                    }
                    if (treeStages.size() > 1) {
                        mixedTrees++;
                    }
                    if (treeKinds.size() > 1) {
                        mixedKinds++;
                    }
                    if (i < 12) {
                        Files.writeString(out.resolve(species.name() + "_" + i + ".json"), scan.json.toString(),
                                StandardCharsets.UTF_8);
                    }
                    // Vanilla recomputes the distance of every leaf, then runs the decay check.
                    for (BlockPos p : scan.leaves) {
                        BlockState before = level.getBlockState(p);
                        before.tick(level, p, random);
                        BlockState after = level.getBlockState(p);
                        if (!after.is(BlockTags.LEAVES)) {
                            decayed++;
                            continue;
                        }
                        if (!after.getValue(LeavesBlock.DISTANCE).equals(before.getValue(LeavesBlock.DISTANCE))) {
                            mismatched++;
                        }
                        after.randomTick(level, p, random);
                        if (!level.getBlockState(p).is(BlockTags.LEAVES)) {
                            decayed++;
                        }
                    }
                    // the fruit is still there after the leaf ticks and holds where it hangs
                    for (BlockPos p : scan.fruit) {
                        BlockState f = level.getBlockState(p);
                        if (!fruitBlocks.contains(f.getBlock()) || !f.canSurvive(level, p)) {
                            looseFruit++;
                        }
                        if (palm && !level.getBlockState(p.above()).is(Blocks.JUNGLE_LEAVES)) {
                            bare++; // a cluster hangs right under the crown's leaves, never lower
                        }
                    }
                    // vines (willow) still hang where they were put: on a leaf, or under a vine with the same face
                    for (BlockPos p : scan.vines) {
                        vines++;
                        BlockState v = level.getBlockState(p);
                        if (!v.is(Blocks.VINE) || !v.canSurvive(level, p)) {
                            looseVines++;
                        }
                    }
                }
                boolean fruitOk = fruitBlocks.isEmpty() ? fruit == 0
                        : minFruit > 0 && looseFruit == 0 && bare == 0 && mixedKinds == 0 && stages[0] > 0
                        && stages[1] > 0 && stages[2] > 0;
                boolean willow = species.name().equals("weeping_willow");
                if (grown < growths || decayed > 0 || mismatched > 0 || !fruitOk || looseVines > 0
                        || (willow && vines == 0)) {
                    failures++;
                }
                lines.add(String.format("%-16s grown %d/%d, height %d-%d, width %d-%d, avg logs %d, avg leaves %d, "
                                + "leaf distance mismatches %d, decayed leaves %d",
                        species.name(), grown, growths, minH, maxH, minW, maxW, logs / Math.max(1, grown),
                        leaves / Math.max(1, grown), mismatched, decayed));
                if (!fruitBlocks.isEmpty()) {
                    lines.add(String.format("%-16s %s: %d in all, %d-%d per tree, green/half/ripe %d/%d/%d, trees "
                                    + "with mixed stages %d/%d, loose %d%s%s", species.name(),
                            String.join("/", fruitBlocks.stream().map(b -> BuiltInRegistries.BLOCK.getKey(b).getPath())
                                    .toList()), fruit, minFruit, maxFruit,
                            stages[0], stages[1], stages[2], mixedTrees, grown, looseFruit,
                            palm ? ", clusters without palm leaves right above " + bare : "",
                            fruitBlocks.size() > 1 ? ", trees with more than one kind " + mixedKinds : ""));
                }

                if (vines > 0 || willow) {
                    lines.add(String.format("%-16s vines: %d in all, loose after the leaf ticks %d", species.name(),
                            vines, looseVines));
                }

                // No room: a ceiling three blocks above the sapling stops the tree, the sapling stays.
                BlockPos blocked = new BlockPos(s * 48, Y, -40);
                platform(level, blocked, Blocks.GRASS_BLOCK);
                for (int dx = -8; dx <= 8; dx++) {
                    for (int dz = -8; dz <= 8; dz++) {
                        level.setBlockAndUpdate(blocked.offset(dx, 3, dz), Blocks.STONE.defaultBlockState());
                    }
                }
                level.setBlockAndUpdate(blocked, species.sapling().defaultBlockState());
                grow(level, blocked, random);
                boolean stayed = level.getBlockState(blocked).is(species.sapling());
                if (!stayed) {
                    failures++;
                }
                lines.add(String.format("%-16s under a ceiling: %s", species.name(),
                        stayed ? "stays a sapling" : "GREW OR VANISHED"));

                failures += plantAndCraft(server, level, species, s, lines);
            }
            failures += dates(level, lines);
            failures += twigs(level, lines);
            failures += leafDrops(level, random, lines);
            failures += olivePit(level, lines);
            failures += pitDamage(level, server, lines);
            failures += creativeTab(level, lines);
            failures += guideBook(server, level, lines);
            failures += palmClumps(lines);
            failures += mulberryKinds(lines);
            failures += willowShapes(out, lines);
            failures += willowReferences(lines);
            failures += ariesWeeping(out, lines);
            failures += squareGrowth(level, random, out, lines, "date_palm", 0, 14);
            failures += squareGrowth(level, random, out, lines, "weeping_willow", 1, 14);
            lines.add(failures == 0 ? "RESULT: all checks passed" : "RESULT: " + failures + " failed checks");
            Files.write(out.resolve("summary.txt"), lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            lines.add("RESULT: could not write output: " + e);
        }
        lines.forEach(l -> System.out.println("[greektrees-selftest] " + l));
        server.halt(false);
    }

    /**
     * Planting: every sapling on grass, only the date palms on sand; the pot holds the sapling. Crafting (shapeless):
     * the recipe gives the sapling, and its recipe-book unlock exists.
     */
    private static int plantAndCraft(MinecraftServer server, ServerLevel level, GreekTrees.Species species, int s,
                                     List<String> lines) {
        int failures = 0;
        boolean palm = species.name().contains("date_palm");
        BlockPos plant = new BlockPos(s * 48, Y, -80);
        platform(level, plant, Blocks.SAND);
        boolean onSand = species.sapling().defaultBlockState().canSurvive(level, plant);
        platform(level, plant, Blocks.GRASS_BLOCK);
        boolean onGrass = species.sapling().defaultBlockState().canSurvive(level, plant);
        boolean pot = ((FlowerPotBlock) species.potted()).getPotted() == species.sapling();
        if (onSand != palm || !onGrass || !pot) {
            failures++;
        }
        lines.add(String.format("%-16s on grass %s, on sand %s, pot %s", species.name(), onGrass, onSand, pot));

        List<Item> ingredients = RECIPES.get(species.name());
        List<ItemStack> stacks = ingredients.stream().map(ItemStack::new).toList();
        CraftingInput input = CraftingInput.of(stacks.size(), 1, stacks);
        ItemStack crafted = server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)
                .map(h -> h.value().assemble(input, level.registryAccess())).orElse(ItemStack.EMPTY);
        boolean unlock = server.getAdvancements()
                .get(GreekTrees.id("recipes/misc/" + species.name() + "_sapling")) != null;
        if (!crafted.is(species.item()) || !unlock) {
            failures++;
        }
        lines.add(String.format("%-16s recipe gives %s, unlock advancement %s", species.name(),
                crafted.isEmpty() ? "NOTHING" : BuiltInRegistries.ITEM.getKey(crafted.getItem()), unlock));
        return failures;
    }

    /**
     * The Aries oak. A single sapling and squares of 2x2 and 3x3 stay saplings, and bone meal is refused on them;
     * sixteen in a 4x4 square grow the giant from any one of them, all sixteen are used up and the trunk covers the
     * square. Every leaf is persistent and outlasts its ticks; vines, glow berries, firefly bushes and slabs hold
     * where they are, 160-200 slabs per tree; no stairs, no glow lichen; about a fifth of the wood is stripped, a
     * fifth of the leaves jungle and a fifth flowering azalea; the side crowns leave room inside (air under leaves).
     * A ceiling over the square keeps all sixteen saplings.
     */
    private static int ariesOak(ServerLevel level, RandomSource random, Path out, List<String> lines)
            throws IOException {
        GreekTrees.Species species = species("aries_oak");
        AriesOakSaplingBlock sapling = (AriesOakSaplingBlock) species.sapling();
        int failures = 0, x0 = 2200;
        // smaller groups wait
        StringBuilder small = new StringBuilder();
        for (int size = 1; size <= 3; size++) {
            BlockPos pos = new BlockPos(x0 + size * 30, Y, -100);
            saplingSquare(level, pos, size, sapling, 0);
            boolean bonemeal = sapling.isValidBonemealTarget(level, pos, level.getBlockState(pos));
            for (int dx = 0; dx < size; dx++) {
                for (int dz = 0; dz < size; dz++) {
                    grow(level, pos.offset(dx, 0, dz), random);
                }
            }
            int left = saplingsIn(level, pos, size, sapling);
            if (left != size * size || bonemeal) {
                failures++;
            }
            small.append(String.format("%s%dx%d: saplings left %d/%d, bone meal %s", size > 1 ? "; " : "", size, size,
                    left, size * size, bonemeal ? "TAKEN" : "refused"));
        }
        lines.add("aries_oak        smaller groups: " + small);
        // a ceiling over a full square
        BlockPos blocked = new BlockPos(x0, Y, -100);
        saplingSquare(level, blocked, 4, sapling, 12);
        for (int dx = -12; dx <= 15; dx++) {
            for (int dz = -12; dz <= 15; dz++) {
                level.setBlockAndUpdate(blocked.offset(dx, 12, dz), Blocks.STONE.defaultBlockState());
            }
        }
        grow(level, blocked.offset(2, 0, 2), random);
        int stayed = saplingsIn(level, blocked, 4, sapling);
        if (stayed != 16) {
            failures++;
        }
        lines.add(String.format("aries_oak        4x4 under a ceiling 12 up: saplings left %d/16", stayed));
        // the giant, from a different sapling of the square each time
        int growths = 4, grown = 0, covered = 0, left = 0, minH = 99, maxH = 0, minW = 99, maxW = 0, logs = 0;
        int leaves = 0, notPersistent = 0, decayed = 0, bonemealOk = 0, room = 0;
        Map<Block, int[]> hanging = new LinkedHashMap<>(); // block -> in all, loose
        for (Block b : new Block[]{Blocks.VINE, Blocks.CAVE_VINES_PLANT, Blocks.CAVE_VINES, Blocks.FERN,
                Blocks.MOSS_BLOCK, Blocks.DARK_OAK_SLAB}) {
            hanging.put(b, new int[2]);
        }
        Map<Block, Integer> kinds = new LinkedHashMap<>(); // all blocks of the trees
        int lichen = 0;
        for (int i = 0; i < growths; i++) {
            BlockPos pos = new BlockPos(x0, Y, i * 100);
            saplingSquare(level, pos, 4, sapling, 30);
            if (sapling.isValidBonemealTarget(level, pos.offset(3, 0, 3), level.getBlockState(pos.offset(3, 0, 3)))) {
                bonemealOk++;
            }
            grow(level, pos.offset(i % 4, 0, (i * 3) % 4), random);
            left += saplingsIn(level, pos, 4, sapling);
            if (!level.getBlockState(pos.offset(1, 0, 1)).is(BlockTags.LOGS)) {
                continue;
            }
            grown++;
            boolean all = true;
            for (int dx = 0; dx < 4; dx++) {
                for (int dz = 0; dz < 4; dz++) {
                    all &= level.getBlockState(pos.offset(dx, 0, dz)).is(BlockTags.LOGS);
                }
            }
            if (all) {
                covered++;
            }
            Scan scan = scan(level, pos.offset(1, 0, 1), 34, 70);
            minH = Math.min(minH, scan.height);
            maxH = Math.max(maxH, scan.height);
            minW = Math.min(minW, scan.width);
            maxW = Math.max(maxW, scan.width);
            logs += scan.logs;
            leaves += scan.leaves.size();
            Files.writeString(out.resolve("aries_oak_" + i + ".json"), scan.json.toString(), StandardCharsets.UTF_8);
            for (BlockPos p : scan.leaves) {
                BlockState before = level.getBlockState(p);
                if (!before.getValue(LeavesBlock.PERSISTENT)) {
                    notPersistent++;
                }
                before.tick(level, p, random);
                level.getBlockState(p).randomTick(level, p, random);
                if (!level.getBlockState(p).is(BlockTags.LEAVES)) {
                    decayed++;
                }
            }
            scan.counts.forEach((b, n) -> kinds.merge(b, n, Integer::sum));
            for (BlockPos p : scan.others) {
                BlockState state = level.getBlockState(p);
                if (state.is(Blocks.GLOW_LICHEN)) {
                    lichen++;
                }
                int[] n = hanging.get(state.getBlock());
                if (n != null) {
                    n[0]++;
                    if (!state.canSurvive(level, p)) {
                        n[1]++;
                    }
                }
            }
            for (BlockPos p : scan.vines) {
                int[] n = hanging.get(Blocks.VINE);
                n[0]++;
                if (!level.getBlockState(p).canSurvive(level, p)) {
                    n[1]++;
                }
            }
            // room in the side crowns: air with leaves above it within six blocks, outside the dome's middle
            for (int dy = 15; dy <= 45; dy++) {
                for (int dx = -34; dx <= 34; dx++) {
                    for (int dz = -34; dz <= 34; dz++) {
                        BlockPos p = pos.offset(1 + dx, dy, 1 + dz);
                        if (dx * dx + dz * dz < 12 * 12 || !level.getBlockState(p).isAir()) {
                            continue;
                        }
                        for (int up = 1; up <= 6; up++) {
                            if (level.getBlockState(p.above(up)).is(BlockTags.LEAVES)) {
                                room++;
                                break;
                            }
                        }
                    }
                }
            }
        }
        StringBuilder hang = new StringBuilder();
        boolean hangOk = true;
        for (Map.Entry<Block, int[]> e : hanging.entrySet()) {
            hang.append(String.format("%s%s %d (loose %d)", hang.isEmpty() ? "" : ", ",
                    BuiltInRegistries.BLOCK.getKey(e.getKey()).getPath(), e.getValue()[0], e.getValue()[1]));
            hangOk &= e.getValue()[0] > 0 && e.getValue()[1] == 0;
        }
        int wood = kinds.getOrDefault(Blocks.DARK_OAK_WOOD, 0), stripped = kinds.getOrDefault(Blocks.STRIPPED_DARK_OAK_WOOD, 0);
        int azalea = kinds.getOrDefault(Blocks.AZALEA_LEAVES, 0), flowering = kinds.getOrDefault(Blocks.FLOWERING_AZALEA_LEAVES, 0);
        int jungleLeaves = kinds.getOrDefault(Blocks.JUNGLE_LEAVES, 0), allLeaves = azalea + flowering + jungleLeaves;
        double strippedShare = 100.0 * stripped / Math.max(1, wood + stripped);
        double jungleShare = 100.0 * jungleLeaves / Math.max(1, allLeaves);
        double floweringShare = 100.0 * flowering / Math.max(1, allLeaves);
        int stairs = kinds.getOrDefault(Blocks.DARK_OAK_STAIRS, 0), slabs = kinds.getOrDefault(Blocks.DARK_OAK_SLAB, 0);
        boolean slabsOk = slabs >= 150 * grown && slabs <= 200 * grown;
        boolean ok = grown == growths && covered == growths && left == 0 && bonemealOk == growths && minH >= 45
                && notPersistent == 0 && decayed == 0 && hangOk && lichen == 0 && stairs == 0 && slabsOk
                && strippedShare > 10 && strippedShare < 30 && jungleShare > 12 && jungleShare < 28
                && floweringShare > 14 && floweringShare < 26;
        if (!ok) {
            failures++;
        }
        lines.add(String.format("aries_oak        from 16 saplings in a 4x4 square: grown %d/%d, trunk covers the "
                        + "square %d, saplings left %d, bone meal taken %d/%d, height %d-%d, width %d-%d, avg logs %d, "
                        + "avg leaves %d, not persistent %d, decayed %d", grown, growths, covered, left, bonemealOk,
                growths, minH, maxH, minW, maxW, logs / Math.max(1, grown), leaves / Math.max(1, grown), notPersistent,
                decayed));
        lines.add("aries_oak        " + hang + String.format(", stairs %d, glow lichen %d, air under the side crowns "
                + "(avg) %d", stairs, lichen, room / Math.max(1, grown)));
        lines.add(String.format("aries_oak        wood %.0f %% stripped; leaves %.0f %% jungle, %.0f %% flowering azalea",
                strippedShare, jungleShare, floweringShare));
        return failures;
    }

    /** Grass round pos (reach blocks out) and size x size of the sapling on its corner pos. */
    private static void saplingSquare(ServerLevel level, BlockPos pos, int size, Block sapling, int reach) {
        platform(level, pos, Blocks.GRASS_BLOCK);
        for (int dx = -reach; dx < size + reach; dx++) {
            for (int dz = -reach; dz < size + reach; dz++) {
                level.setBlockAndUpdate(pos.offset(dx, -1, dz), Blocks.GRASS_BLOCK.defaultBlockState());
            }
        }
        for (int dx = 0; dx < size; dx++) {
            for (int dz = 0; dz < size; dz++) {
                level.setBlockAndUpdate(pos.offset(dx, 0, dz), sapling.defaultBlockState());
            }
        }
    }

    private static int saplingsIn(ServerLevel level, BlockPos pos, int size, Block sapling) {
        int n = 0;
        for (int dx = 0; dx < size; dx++) {
            for (int dz = 0; dz < size; dz++) {
                if (level.getBlockState(pos.offset(dx, 0, dz)).is(sapling)) {
                    n++;
                }
            }
        }
        return n;
    }

    /**
     * Dates: edible; planted with a right click on the side of jungle wood; a ripe cluster gives 2-3 when broken, an
     * unripe one 1; the right-click rules of {@link #clickRules}; clusters hold on jungle wood but not on oak; the hit
     * box of every facing touches the trunk.
     */
    private static int dates(ServerLevel level, List<String> lines) {
        boolean edible = new ItemStack(GreekTrees.DATE).has(DataComponents.FOOD);
        BlockPos wood = new BlockPos(600, Y, 0);
        level.setBlockAndUpdate(wood, Blocks.JUNGLE_WOOD.defaultBlockState());
        BlockPos at = wood.south();
        // planting: a date in hand against the side of jungle wood hangs a young cluster there
        ItemStack dates = new ItemStack(GreekTrees.DATE, 5);
        rightClick(level, wood, Direction.SOUTH, dates);
        BlockState planted = level.getBlockState(at);
        boolean plants = planted.is(GreekTrees.DATE_CLUSTER) && planted.getValue(CocoaBlock.AGE) == 0
                && planted.getValue(CocoaBlock.FACING) == Direction.NORTH && dates.getCount() == 4;
        BlockState ripe = GreekTrees.DATE_CLUSTER.defaultBlockState().setValue(CocoaBlock.FACING, Direction.NORTH)
                .setValue(CocoaBlock.AGE, CocoaBlock.MAX_AGE);
        boolean holdsOnJungle = ripe.canSurvive(level, at);
        int[] broken = dropRange(level, ripe, at, GreekTrees.DATE);
        int young = unripeDrops(level, ripe, CocoaBlock.AGE, at, GreekTrees.DATE);
        String clicks = clickRules(level, at, ripe, CocoaBlock.AGE, GreekTrees.DATE);
        level.setBlockAndUpdate(at, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(wood, Blocks.OAK_WOOD.defaultBlockState());
        boolean holdsOnOak = ripe.canSurvive(level, at);
        // hit box: the ripe cluster is wide, and on every side it reaches the trunk face
        boolean boxes = true;
        double width = 0;
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            AABB box = ripe.setValue(CocoaBlock.FACING, facing).getShape(level, at, CollisionContext.empty()).bounds();
            double toTrunk = switch (facing) {
                case NORTH -> box.minZ;
                case SOUTH -> 1 - box.maxZ;
                case WEST -> box.minX;
                default -> 1 - box.maxX;
            };
            width = Math.max(box.getXsize(), box.getZsize());
            boxes &= toTrunk == 0 && box.getYsize() > 0.8;
        }
        boolean ok = edible && plants && holdsOnJungle && !holdsOnOak && broken[0] >= 2 && broken[1] <= 3
                && young == 1 && clicks.isEmpty() && boxes;
        lines.add(String.format("date: edible %s, planted on jungle wood %s, cluster holds on jungle wood %s / on oak "
                        + "%s, broken ripe %d-%d, unripe %d, right clicks %s, hit boxes reach the trunk %s (ripe %.2f "
                        + "wide)", edible, plants, holdsOnJungle, holdsOnOak, broken[0], broken[1], young,
                clicks.isEmpty() ? "as wanted" : "WRONG: " + clicks, boxes, width));
        return ok ? 0 : 1;
    }

    /**
     * Olive, fig and arbutus twigs: planted with a right click on any face of a leaf block (the twig hangs under it),
     * hang under leaves only, fall when the leaf goes; broken and right-clicked like the dates; an olive gives its pit
     * back when eaten.
     */
    private static int twigs(ServerLevel level, List<String> lines) {
        int failures = 0;
        FoodProperties food = new ItemStack(GreekTrees.OLIVE).get(DataComponents.FOOD);
        boolean pitBack = food != null && food.usingConvertsTo().filter(s -> s.is(GreekTrees.OLIVE_PIT)).isPresent();
        Object[][] twigs = {{GreekTrees.OLIVE_TWIG, GreekTrees.OLIVE, Blocks.AZALEA_LEAVES},
                {GreekTrees.FIG_TWIG, GreekTrees.FIG, Blocks.AZALEA_LEAVES},
                {GreekTrees.ARBUTUS_TWIG, GreekTrees.ARBUTUS_BERRY, Blocks.MANGROVE_LEAVES},
                {GreekTrees.BLACK_MULBERRY_TWIG, GreekTrees.BLACK_MULBERRY, Blocks.JUNGLE_LEAVES},
                {GreekTrees.WHITE_MULBERRY_TWIG, GreekTrees.WHITE_MULBERRY, Blocks.JUNGLE_LEAVES},
                {GreekTrees.RED_MULBERRY_TWIG, GreekTrees.RED_MULBERRY, Blocks.JUNGLE_LEAVES}};
        for (int i = 0; i < twigs.length; i++) {
            Block twig = (Block) twigs[i][0];
            Item item = (Item) twigs[i][1];
            BlockPos leaf = new BlockPos(620 + i * 10, Y + 5, 0);
            level.setBlockAndUpdate(leaf, ((Block) twigs[i][2]).defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
            BlockPos at = leaf.below();
            boolean edible = new ItemStack(item).has(DataComponents.FOOD);
            // planting: a right click on the side of the leaf block hangs a young twig under it
            ItemStack fruit = new ItemStack(item, 5);
            rightClick(level, leaf, Direction.NORTH, fruit);
            BlockState planted = level.getBlockState(at);
            boolean plants = planted.is(twig) && planted.getValue(HangingFruitBlock.STAGE) == 0 && fruit.getCount() == 4;
            BlockState ripe = twig.defaultBlockState().setValue(HangingFruitBlock.STAGE, HangingFruitBlock.MAX_AGE);
            boolean underLeaves = ripe.canSurvive(level, at);
            int[] broken = dropRange(level, ripe, at, item);
            int young = unripeDrops(level, ripe, HangingFruitBlock.STAGE, at, item);
            String clicks = clickRules(level, at, ripe, HangingFruitBlock.STAGE, item);
            // taking the leaf away brings the twig down
            level.setBlockAndUpdate(at, ripe);
            level.setBlockAndUpdate(leaf, Blocks.AIR.defaultBlockState());
            boolean falls = level.getBlockState(at).isAir();
            // harvest mods take any block with an "age" property for a crop that is ripe at every stage
            boolean noAge = twig.getStateDefinition().getProperty("age") == null;
            boolean ok = edible && plants && underLeaves && falls && broken[0] >= 2 && broken[1] <= 3 && young == 1
                    && clicks.isEmpty() && noAge && (i > 0 || pitBack);
            if (!ok) {
                failures++;
            }
            lines.add(String.format("%s: edible %s, planted under leaves %s, twig holds under leaves %s, falls "
                            + "without them %s, broken ripe %d-%d, unripe %d, right clicks %s, no age property %s%s",
                    BuiltInRegistries.ITEM.getKey(item).getPath(), edible, plants, underLeaves, falls, broken[0],
                    broken[1], young, clicks.isEmpty() ? "as wanted" : "WRONG: " + clicks, noAge,
                    i == 0 ? ", eating gives the pit back " + pitBack : ""));
        }
        return failures;
    }

    /**
     * The right clicks on a fruit block at pos (ripe = its ripe state), each with an empty hand, its own fruit and a
     * dirt block in hand: a ripe one is harvested and starts over at the first stage, an unripe one ignores the click
     * (nothing placed, nothing used up); bone meal still makes an unripe one grow. Returns what went wrong, empty when
     * all is as wanted.
     */
    private static String clickRules(ServerLevel level, BlockPos pos, BlockState ripe, IntegerProperty age, Item fruit) {
        List<String> wrong = new ArrayList<>();
        ItemStack[] hands = {ItemStack.EMPTY, new ItemStack(fruit, 5), new ItemStack(Items.DIRT, 5)};
        String[] names = {"empty hand", "its fruit", "dirt"};
        for (int h = 0; h < hands.length; h++) {
            level.setBlockAndUpdate(pos, ripe);
            ItemStack held = hands[h].copy();
            rightClick(level, pos, Direction.SOUTH, held);
            BlockState after = level.getBlockState(pos);
            if (!after.is(ripe.getBlock()) || after.getValue(age) != HangingFruitBlock.PICKED_AGE
                    || held.getCount() != hands[h].getCount()) {
                wrong.add("ripe with " + names[h]);
            }
            BlockState unripe = ripe.setValue(age, 1);
            level.setBlockAndUpdate(pos, unripe);
            held = hands[h].copy();
            rightClick(level, pos, Direction.SOUTH, held);
            if (!level.getBlockState(pos).equals(unripe) || held.getCount() != hands[h].getCount()
                    || !level.getBlockState(pos.south()).isAir()) {
                wrong.add("unripe with " + names[h]);
            }
        }
        level.setBlockAndUpdate(pos, ripe.setValue(age, 0));
        rightClick(level, pos, Direction.SOUTH, new ItemStack(Items.BONE_MEAL, 5));
        if (level.getBlockState(pos).getValue(age) != 1) {
            wrong.add("bone meal");
        }
        return String.join(", ", wrong);
    }

    /**
     * Leaves of a grown tree drop its fruit now and then; the same leaves next to the log a vanilla tree is made of
     * never do (jungle tree, azalea tree, mangrove), and neither do the leaves of the cypress, which shares wood and
     * leaves with the fig.
     */
    private static int leafDrops(ServerLevel level, RandomSource random, List<String> lines) {
        int failures = 0;
        Object[][] cases = {{"date_palm", GreekTrees.DATE, Blocks.JUNGLE_LEAVES, Blocks.JUNGLE_LOG},
                {"olive", GreekTrees.OLIVE, Blocks.AZALEA_LEAVES, Blocks.OAK_LOG},
                {"fig", GreekTrees.FIG, Blocks.AZALEA_LEAVES, "cypress"},
                {"strawberry_tree", GreekTrees.ARBUTUS_BERRY, Blocks.MANGROVE_LEAVES, Blocks.MANGROVE_LOG},
                {"mulberry", null, Blocks.JUNGLE_LEAVES, "date_palm"}}; // the kind the grown tree carries
        for (int c = 0; c < cases.length; c++) {
            Scan grown = growForScan(level, random, species((String) cases[c][0]), new BlockPos(700 + c * 100, Y, 0));
            List<BlockPos> treeLeaves = grown.leaves;
            Item item = cases[c][1] != null ? (Item) cases[c][1]
                    : level.getBlockState(grown.fruit.getFirst()).getBlock().asItem();
            List<Item> otherKinds = new ArrayList<>();
            if (cases[c][1] == null) {
                for (Block twig : GreekTrees.MULBERRY_TWIGS) {
                    if (twig.asItem() != item) {
                        otherKinds.add(twig.asItem());
                    }
                }
            }
            List<BlockPos> otherLeaves;
            String other;
            if (cases[c][3] instanceof String name) {
                otherLeaves = growForLeaves(level, random, species(name), new BlockPos(700 + c * 100, Y, 60));
                other = name + " leaves";
            } else {
                BlockPos log = new BlockPos(700 + c * 100, Y, 60);
                for (int y = 0; y < 6; y++) {
                    level.setBlockAndUpdate(log.above(y), ((Block) cases[c][3]).defaultBlockState());
                }
                level.setBlockAndUpdate(log.offset(1, 4, 0), ((Block) cases[c][2]).defaultBlockState());
                otherLeaves = List.of(log.offset(1, 4, 0));
                other = BuiltInRegistries.BLOCK.getKey((Block) cases[c][2]).getPath() + " next to "
                        + BuiltInRegistries.BLOCK.getKey((Block) cases[c][3]).getPath();
            }
            int rolls = 3000, fromTree = 0, fromOther = 0, wrongKind = 0;
            for (int i = 0; i < rolls; i++) {
                BlockPos p = treeLeaves.get(i % treeLeaves.size());
                List<ItemStack> drops = Block.getDrops(level.getBlockState(p), level, p, null);
                fromTree += count(drops, item);
                for (Item kind : otherKinds) {
                    wrongKind += count(drops, kind);
                }
                BlockPos o = otherLeaves.get(i % otherLeaves.size());
                fromOther += count(Block.getDrops(level.getBlockState(o), level, o, null), item);
            }
            if (fromTree == 0 || fromOther > 0 || wrongKind > 0) {
                failures++;
            }
            lines.add(String.format("leaf drops over %d breaks: %s leaves %d %s (%.1f %%), %s %d%s", rolls,
                    cases[c][0], fromTree, BuiltInRegistries.ITEM.getKey(item).getPath(), 100.0 * fromTree / rolls,
                    other, fromOther, otherKinds.isEmpty() ? "" : ", other kinds " + wrongKind));
        }
        return failures;
    }

    private static List<BlockPos> growForLeaves(ServerLevel level, RandomSource random, GreekTrees.Species species,
                                                BlockPos pos) {
        return growForScan(level, random, species, pos).leaves;
    }

    private static Scan growForScan(ServerLevel level, RandomSource random, GreekTrees.Species species, BlockPos pos) {
        platform(level, pos, Blocks.GRASS_BLOCK);
        level.setBlockAndUpdate(pos, species.sapling().defaultBlockState());
        grow(level, pos, random);
        return scan(level, pos);
    }

    /**
     * A pit hitting a pig takes 1.5 hearts (3 health), a sharpened pit 3.5 hearts (7). Two pits
     * on top of each other in the crafting grid make a sharpened one.
     */
    private static int pitDamage(ServerLevel level, MinecraftServer server, List<String> lines) {
        int failures = 0;
        StringBuilder line = new StringBuilder("pit damage:");
        Item[] pits = {GreekTrees.OLIVE_PIT, GreekTrees.SHARPENED_OLIVE_PIT};
        float[] want = {ThrownOlivePit.DAMAGE, ThrownOlivePit.SHARPENED_DAMAGE};
        for (int i = 0; i < pits.length; i++) {
            BlockPos ground = new BlockPos(1200 + i * 20, Y, 0);
            platform(level, ground, Blocks.STONE);
            Pig pig = EntityType.PIG.create(level);
            pig.setPos(ground.getX() + 0.5, ground.getY(), ground.getZ() + 3.5);
            float before = pig.getHealth();
            ThrownOlivePit pit = (ThrownOlivePit) ((OlivePitItem) pits[i]).asProjectile(level,
                    new Vec3(ground.getX() + 0.5, ground.getY() + 0.5, ground.getZ() + 0.5), new ItemStack(pits[i]),
                    Direction.SOUTH);
            level.addFreshEntity(pit);
            // Entities this far from spawn are not visible to collision checks without players nearby, so the hit
            // is handed to the pit directly; finding the hit in flight is vanilla's projectile code.
            pit.onHit(new EntityHitResult(pig));
            float taken = before - pig.getHealth();
            if (Math.abs(taken - want[i]) > 0.01f || !pit.isRemoved()) {
                failures++;
            }
            line.append(String.format(" %s %.1f hearts (want %.1f), breaks %s;",
                    BuiltInRegistries.ITEM.getKey(pits[i]).getPath(), taken / 2, want[i] / 2, pit.isRemoved()));
        }
        CraftingInput two = CraftingInput.of(1, 2, List.of(new ItemStack(GreekTrees.OLIVE_PIT),
                new ItemStack(GreekTrees.OLIVE_PIT)));
        ItemStack sharpened = server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, two, level)
                .map(h -> h.value().assemble(two, level.registryAccess())).orElse(ItemStack.EMPTY);
        if (!sharpened.is(GreekTrees.SHARPENED_OLIVE_PIT)) {
            failures++;
        }
        line.append(" two pits on top of each other give ").append(sharpened.isEmpty() ? "NOTHING"
                : BuiltInRegistries.ITEM.getKey(sharpened.getItem()).getPath());
        lines.add(line.toString());
        return failures;
    }

    /** A pit dropped onto stone flies, hits the ground and breaks, like a snowball. */
    private static int olivePit(ServerLevel level, List<String> lines) {
        BlockPos ground = new BlockPos(1400, Y, 0); // clear of the trees grown for the leaf drops (x 700-1100)
        platform(level, ground, Blocks.STONE);
        ThrownOlivePit pit = (ThrownOlivePit) ((OlivePitItem) GreekTrees.OLIVE_PIT).asProjectile(level,
                new Vec3(ground.getX() + 0.5, ground.getY() + 4, ground.getZ() + 0.5), new ItemStack(GreekTrees.OLIVE_PIT),
                Direction.DOWN);
        pit.shoot(0, -1, 0, 1.5f, 0);
        level.addFreshEntity(pit);
        int ticks = 0;
        while (!pit.isRemoved() && ticks < 100) {
            pit.tick();
            ticks++;
        }
        boolean breaks = pit.isRemoved();
        double endY = pit.getY();
        lines.add(String.format("olive pit: thrown down from 4 blocks, breaks on the ground %s after %d ticks (y %.2f)",
                breaks, ticks, endY - ground.getY()));
        return breaks && endY - ground.getY() < 1.5 ? 0 : 1;
    }

    /**
     * Large date palm layouts, rolled as shapes only (no world): 2-5 trunks, side trunks also on diagonal feet, no
     * two trunk blocks side by side at the same height (a single trunk never has that, so any pair belongs to two
     * trunks), every date cluster right under a leaf.
     */
    private static int palmClumps(List<String> lines) {
        int rolls = 500;
        int failures = smallPalms(lines, rolls) + palmCrowns(lines);
        int[] byCount = new int[7];
        int diagonal = 0, sideBySide = 0, maxHeight = 0, bare = 0;
        for (int seed = 0; seed < rolls; seed++) {
            Map<BlockPos, Shape.Cell> cells = TreeShapes.largeDatePalm(RandomSource.create(seed)).cells();
            bare += bareClusters(cells);
            Set<BlockPos> logs = new HashSet<>();
            cells.forEach((p, c) -> {
                if (c.kind() == Shape.Kind.LOG) {
                    logs.add(p);
                }
            });
            int feet = 0;
            for (BlockPos p : logs) {
                if (p.getY() == 0) {
                    feet++;
                    if (p.getX() != 0 && p.getZ() != 0) {
                        diagonal++;
                    }
                }
                maxHeight = Math.max(maxHeight, p.getY());
                for (BlockPos q : List.of(p.east(), p.south())) {
                    if (logs.contains(q)) {
                        sideBySide++;
                    }
                }
            }
            byCount[Math.min(feet, 6)]++;
        }
        boolean ok = byCount[0] + byCount[1] + byCount[6] == 0 && byCount[2] > 0 && byCount[3] > 0 && byCount[4] > 0
                && byCount[5] > 0 && diagonal > 0 && sideBySide == 0 && bare == 0;
        lines.add(String.format("large date palm layouts over %d rolls: trunks 2/3/4/5 = %d/%d/%d/%d (other %d), side "
                        + "trunks on diagonal feet %d, trunk blocks side by side %d, highest log y %d, date clusters "
                        + "without palm leaves right above %d", rolls, byCount[2], byCount[3], byCount[4], byCount[5],
                byCount[0] + byCount[1] + byCount[6], diagonal, sideBySide, maxHeight, bare));
        return failures + (ok ? 0 : 1);
    }

    /** Date clusters of a rolled palm without a leaf right above them. */
    private static int bareClusters(Map<BlockPos, Shape.Cell> cells) {
        int n = 0;
        for (Map.Entry<BlockPos, Shape.Cell> e : cells.entrySet()) {
            n += e.getValue().state().is(GreekTrees.DATE_CLUSTER) && !isLeaf(cells, e.getKey().above()) ? 1 : 0;
        }
        return n;
    }

    /**
     * Palm crowns rolled alone, 200 of each size from one random source, compared as their leaves around the core:
     * at least 150 different ones per size, none the same after a quarter turn, every leaf joined to the core over
     * faces, edges or corners.
     */
    private static int palmCrowns(List<String> lines) {
        RandomSource r = RandomSource.create(11);
        StringBuilder line = new StringBuilder("palm crowns over 200 rolls per size:");
        boolean ok = true;
        for (int size = 0; size <= 2; size++) {
            Set<Set<BlockPos>> seen = new HashSet<>();
            int even = 0, apart = 0;
            for (int roll = 0; roll < 200; roll++) {
                Set<BlockPos> leaves = new HashSet<>();
                TreeShapes.palmCrown(r, size).cells().forEach((p, c) -> {
                    if (c.kind() == Shape.Kind.LEAF) {
                        leaves.add(p.below()); // relative to the core at 0, 1, 0
                    }
                });
                seen.add(leaves);
                Set<BlockPos> turned = new HashSet<>();
                leaves.forEach(p -> turned.add(new BlockPos(-p.getZ(), p.getY(), p.getX())));
                even += turned.equals(leaves) ? 1 : 0;
                apart += leaves.size() + 1 - joined(leaves, Integer.MAX_VALUE).size();
            }
            ok &= seen.size() >= 150 && even == 0 && apart == 0;
            line.append(String.format(" size %d: %d different, the same after a quarter turn %d, leaves apart from the "
                    + "core %d;", size, seen.size(), even, apart));
        }
        lines.add(line.toString());
        return ok ? 0 : 1;
    }

    /**
     * Date palms of one sapling, rolled as shapes only: one to three trunks, their feet within a 2x2 square (two on
     * a diagonal, three in an L); no two trunk blocks side by side at the same height above the lowest three layers;
     * a single trunk slants along one axis and never back (its blocks spread along x or along z, not both, and every
     * layer is where the one below is or one block further the same way); every leaf is persistent jungle leaves.
     */
    private static int smallPalms(List<String> lines, int rolls) {
        int[] byCount = new int[5];
        int sideBySide = 0, winding = 0, straight = 0, otherLeaves = 0, loosePersistence = 0, bare = 0, badFeet = 0;
        int lean = 0;
        RandomSource r = RandomSource.create(7); // one source for all: small seeds in a row start alike
        for (int roll = 0; roll < rolls; roll++) {
            Shape shape = TreeShapes.datePalm(r);
            loosePersistence += shape.persistentLeaves() ? 0 : 1;
            bare += bareClusters(shape.cells());
            Map<Integer, BlockPos> layers = new java.util.TreeMap<>(); // of a single trunk: its block per height
            List<BlockPos> feetAt = new ArrayList<>();
            for (Map.Entry<BlockPos, Shape.Cell> e : shape.cells().entrySet()) {
                BlockPos p = e.getKey();
                if (e.getValue().kind() == Shape.Kind.LEAF) {
                    otherLeaves += e.getValue().state().is(Blocks.JUNGLE_LEAVES) ? 0 : 1;
                } else if (e.getValue().kind() == Shape.Kind.LOG) {
                    if (p.getY() == 0) {
                        feetAt.add(p);
                    }
                    layers.put(p.getY(), p);
                    for (BlockPos q : List.of(p.east(), p.south())) {
                        Shape.Cell c = shape.cells().get(q);
                        sideBySide += p.getY() >= 3 && c != null && c.kind() == Shape.Kind.LOG ? 1 : 0;
                    }
                }
            }
            int feet = feetAt.size();
            byCount[Math.min(feet, 4)]++;
            int spanX = feetAt.stream().mapToInt(BlockPos::getX).max().orElse(0)
                    - feetAt.stream().mapToInt(BlockPos::getX).min().orElse(0);
            int spanZ = feetAt.stream().mapToInt(BlockPos::getZ).max().orElse(0)
                    - feetAt.stream().mapToInt(BlockPos::getZ).min().orElse(0);
            badFeet += feet > 1 && (spanX != 1 || spanZ != 1) ? 1 : 0; // two on a diagonal or three in an L of a 2x2
            if (feet == 1) {
                lean += Math.abs(layers.get(layers.size() - 1).getX()) + Math.abs(layers.get(layers.size() - 1).getZ());
            }
            if (feet == 1) {
                BlockPos top = layers.get(layers.size() - 1);
                int dx = Integer.signum(top.getX()), dz = Integer.signum(top.getZ());
                boolean ok = dx == 0 || dz == 0;
                for (int y = 1; y < layers.size() && ok; y++) {
                    int sx = layers.get(y).getX() - layers.get(y - 1).getX(), sz = layers.get(y).getZ() - layers.get(y - 1).getZ();
                    ok = (sx == 0 && sz == 0) || (sx == dx && sz == dz);
                }
                winding += ok ? 0 : 1;
                straight += dx == 0 && dz == 0 ? 1 : 0;
            }
        }
        boolean ok = byCount[0] + byCount[4] == 0 && byCount[1] > 0 && byCount[2] > 0 && byCount[3] > 0
                && sideBySide == 0 && winding == 0 && straight == 0 && otherLeaves == 0 && loosePersistence == 0
                && bare == 0 && badFeet == 0;
        lines.add(String.format("date palm shapes over %d rolls: trunks 1/2/3 = %d/%d/%d (other %d), feet not within a "
                        + "2x2 square %d, trunk blocks side by side from layer 3 up %d, single trunks winding %d, single "
                        + "trunks without a slant %d, their lean %.2f blocks on average, other leaves than jungle %d, "
                        + "shapes without persistent leaves %d, date clusters without palm leaves right above %d", rolls,
                byCount[1], byCount[2], byCount[3], byCount[0] + byCount[4], badFeet, sideBySide, winding, straight,
                (double) lean / Math.max(1, byCount[1]), otherLeaves, loosePersistence, bare));
        return ok ? 0 : 1;
    }

    /**
     * Weeping willows rolled as shapes only (300 from one sapling, 100 big ones): no trunk stands straight (only a
     * straight trunk keeps wood in its foot column, or all four of the 2x2 square, from the foot to the highest log);
     * the sizes spread; every tree has strands reaching down to one or two blocks above the ground, no leaf hangs
     * lower, and no two strand ends hang side by side or diagonally beside each other; all leaves are mangrove leaves.
     * The trunks (see {@link #trunkKind}): no maze and no fault, 55-75 % bend, the rest a C, at least 40 (big: 60)
     * different ones from the third layer up and none more than 15 % of the rolls. The first nine trunks of each
     * size are written out for concept/render_selftest.py (willow_trunks.png).
     */
    private static int willowShapes(Path out, List<String> lines) throws IOException {
        int failures = 0;
        for (int size = 1; size <= 2; size++) {
            int rolls = size == 1 ? 300 : 100, straight = 0, noStrand = 0, tooLow = 0, beside = 0, otherLeaves = 0;
            int minH = 99, maxH = 0, rimStrands = 0, innerStrands = 0, underStrands = 0;
            int[] faults = new int[7];
            Map<String, Integer> kinds = new LinkedHashMap<>(), forms = new HashMap<>();
            RandomSource r = RandomSource.create(size); // one source for all: small seeds in a row start alike
            for (int roll = 0; roll < rolls; roll++) {
                Shape shape = size == 1 ? TreeShapes.weepingWillow(r) : TreeShapes.largeWeepingWillow(r);
                Map<BlockPos, Shape.Cell> cells = shape.cells();
                rimStrands += shape.strandCounts()[0];
                innerStrands += shape.strandCounts()[1];
                underStrands += shape.strandCounts()[2];
                int th = shape.trunkTop();
                Set<BlockPos> trunk = joined(logs(cells), th);
                kinds.merge(trunkKind(cells, trunk, th, size == 2, faults), 1, Integer::sum);
                forms.merge(form(trunk, 2, th), 1, Integer::sum);
                if (roll < 9) {
                    StringBuilder json = new StringBuilder("[\n");
                    trunk.forEach(p -> jsonLine(json, p.getX(), p.getY(), p.getZ(), cells.get(p).state()));
                    Files.writeString(out.resolve("willow_trunk_roll_" + (size == 1 ? "small_" : "big_") + roll
                            + ".json"), json.append("\n]\n"), StandardCharsets.UTF_8);
                }
                int top = 0, logTop = 0;
                List<BlockPos> ends = new ArrayList<>(); // leaves one or two above the ground: the strands' ends
                for (Map.Entry<BlockPos, Shape.Cell> e : cells.entrySet()) {
                    BlockPos p = e.getKey();
                    top = Math.max(top, p.getY());
                    if (e.getValue().kind() == Shape.Kind.LOG) {
                        logTop = Math.max(logTop, p.getY());
                    } else if (e.getValue().kind() == Shape.Kind.LEAF) {
                        otherLeaves += e.getValue().state().is(Blocks.MANGROVE_LEAVES) ? 0 : 1;
                        if (p.getY() < 1) {
                            tooLow++;
                        } else if (p.getY() <= 2) {
                            ends.add(p);
                        }
                    }
                }
                boolean upright = true;
                for (int y = 0; y <= logTop; y++) {
                    for (int dx = 0; dx < size; dx++) {
                        for (int dz = 0; dz < size; dz++) {
                            Shape.Cell c = cells.get(new BlockPos(dx, y, dz));
                            upright &= c != null && c.kind() == Shape.Kind.LOG;
                        }
                    }
                }
                straight += upright ? 1 : 0;
                noStrand += ends.isEmpty() ? 1 : 0;
                for (BlockPos p : ends) {
                    for (BlockPos q : ends) {
                        if (!p.equals(q) && p.getY() == q.getY() && Math.abs(p.getX() - q.getX()) <= 1
                                && Math.abs(p.getZ() - q.getZ()) <= 1) {
                            beside++;
                        }
                    }
                }
                minH = Math.min(minH, top + 1);
                maxH = Math.max(maxH, top + 1);
            }
            int bends = kinds.getOrDefault("bend", 0), cs = kinds.getOrDefault("C", 0);
            int most = forms.values().stream().max(Integer::compare).orElse(0);
            boolean ok = straight == 0 && noStrand == 0 && tooLow == 0 && beside == 0 && otherLeaves == 0
                    && maxH - minH >= 6 && bends + cs == rolls && bends >= 0.55 * rolls && bends <= 0.75 * rolls
                    && java.util.Arrays.stream(faults).sum() == 0 && forms.size() >= (size == 1 ? 40 : 60)
                    && most <= 0.15 * rolls;
            failures += ok ? 0 : 1;
            String name = size == 1 ? "weeping willow" : "big weeping willow";
            lines.add(String.format("%s shapes over %d rolls: straight trunks %d, height %d-%d, trees without strands "
                            + "to the ground %d, leaves at y 0 %d, strand ends side by side %d, other leaves than mangrove "
                            + "%d, strands per tree at the rim %.1f, further in %.1f (more than 3 in from the rim "
                            + "%.1f)", name, rolls, straight, minH, maxH, noStrand, tooLow, beside / 2, otherLeaves,
                    (double) rimStrands / rolls, (double) innerStrands / rolls, (double) underStrands / rolls));
            lines.add(String.format("%s trunks over %d rolls: bend %d %%, C %d %%, maze (more than two moves or another "
                            + "turn) %d; layers with too few or many blocks %d, blocks without wood on a face %d, "
                            + "layers sharing under 3 blocks with the one below %d, blocks over one beside the foot "
                            + "%d, top layers not plain or without the leader %d, thin diagonal bends %d, big C lanes "
                            + "only shifted %d; different trunks %d, the most common "
                            + "%d times", name, rolls, 100 * bends / rolls, 100 * cs / rolls, rolls - bends - cs,
                    faults[0], faults[1], faults[2], faults[3], faults[4], faults[5], faults[6], forms.size(), most));
        }
        return failures;
    }

    /**
     * Reads a willow trunk (its wood joined to the foot up to its top layer th) as a bend, a C or a maze, and counts
     * its faults: 0 layers from y 1 holding fewer or more blocks than 1-3 (big: 4-6), 1 blocks without wood on a face,
     * 2 big layers sharing fewer than three blocks with the one below, 3 big blocks further than a block beside the
     * foot square, 4 a top layer that is not plain or has no leader on it, 5 a thin bend that moves diagonally (reads
     * as a zigzag), 6 lanes of a big C along an axis that jump out and back without a layer of three blocks (a piece
     * of trunk merely shifted). Its plain layers (one block, a 2x2 square on the big one) show where it stands; a
     * bend moves over once or twice the same way, a C out and back.
     */
    private static String trunkKind(Map<BlockPos, Shape.Cell> cells, Set<BlockPos> trunk, int th, boolean big,
                                    int[] faults) {
        List<Set<BlockPos>> layers = new ArrayList<>();
        for (int y = 0; y <= th; y++) {
            layers.add(new HashSet<>());
        }
        trunk.forEach(p -> layers.get(p.getY()).add(new BlockPos(p.getX(), 0, p.getZ())));
        List<BlockPos> stands = new ArrayList<>(List.of(BlockPos.ZERO)); // from the foot up, each change once
        for (int y = 1; y <= th; y++) {
            Set<BlockPos> layer = layers.get(y);
            faults[0] += layer.size() < (big ? 4 : 1) || layer.size() > (big ? 6 : 3) ? 1 : 0;
            if (big) {
                Set<BlockPos> shared = new HashSet<>(layer);
                shared.retainAll(layers.get(y - 1));
                faults[2] += shared.size() < 3 ? 1 : 0;
                for (BlockPos p : layer) {
                    faults[3] += p.getX() < -1 || p.getX() > 2 || p.getZ() < -1 || p.getZ() > 2 ? 1 : 0;
                }
            }
            BlockPos at = plainCorner(layer, big);
            if (at != null && !at.equals(stands.getLast())) {
                stands.add(at);
            }
            if (y == th) {
                boolean led = at != null;
                for (BlockPos p : layer) {
                    led &= isLog(cells, p.above(th + 1));
                }
                faults[4] += led ? 0 : 1;
            }
        }
        for (BlockPos p : trunk) {
            faults[1] += java.util.Arrays.stream(Direction.values()).anyMatch(d -> isLog(cells, p.relative(d)))
                    ? 0 : 1;
        }
        List<BlockPos> moves = new ArrayList<>();
        for (int i = 1; i < stands.size(); i++) {
            moves.add(stands.get(i).subtract(stands.get(i - 1)));
        }
        if (moves.size() == 1 || moves.size() == 2 && moves.get(0).equals(moves.get(1))) {
            faults[5] += !big && moves.getFirst().getX() != 0 && moves.getFirst().getZ() != 0 ? 1 : 0;
            return "bend";
        }
        if (moves.size() != 2 || !moves.get(0).equals(BlockPos.ZERO.subtract(moves.get(1)))) {
            return "maze";
        }
        if (big && (moves.getFirst().getX() == 0 || moves.getFirst().getZ() == 0)) {
            // out and back along an axis: each lane across the move holds three blocks in some layer
            boolean alongX = moves.getFirst().getX() != 0;
            for (int lane = 0; lane <= 1; lane++) {
                int l = lane;
                boolean soft = false;
                for (int y = 2; y <= th; y++) {
                    soft |= layers.get(y).stream().filter(p -> (alongX ? p.getZ() : p.getX()) == l).count() >= 3;
                }
                faults[6] += soft ? 0 : 1;
            }
        }
        return "C";
    }

    /** Where a trunk stands in a plain layer (its one block, or its 2x2 square's corner), null for other layers. */
    private static BlockPos plainCorner(Set<BlockPos> layer, boolean big) {
        if (layer.size() != (big ? 4 : 1)) {
            return null;
        }
        BlockPos c = layer.stream().min(Comparator.<BlockPos>comparingInt(p -> p.getX()).thenComparingInt(p -> p.getZ()))
                .orElseThrow();
        return !big || layer.containsAll(List.of(c.east(), c.south(), c.east().south())) ? c : null;
    }

    /**
     * Each thin willow trunk the user rebuilt by hand (concept/reference/willow_trunk_small_*.json, layers 1 to the
     * top) turns up among 20 000 rolled thin willows from one random source, up to a turn or a mirror.
     */
    private static int willowReferences(List<String> lines) throws IOException {
        Path dir = FabricLoader.getInstance().getGameDir().resolve("../concept/reference");
        Map<String, Integer> heights = Map.of("small_1", 5, "small_2", 9, "small_3", 9); // measured, PLAN-0.17.md E.1
        Map<String, String> wanted = new LinkedHashMap<>();
        for (String name : List.of("small_1", "small_2", "small_3")) {
            Set<BlockPos> wood = new HashSet<>();
            for (JsonElement e : JsonParser.parseString(Files.readString(dir.resolve("willow_trunk_" + name + ".json")))
                    .getAsJsonArray()) {
                JsonArray a = e.getAsJsonArray();
                wood.add(new BlockPos(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt()));
            }
            wanted.put(name, form(joined(wood, heights.get(name)), 1, heights.get(name)));
        }
        Map<String, Integer> found = new LinkedHashMap<>();
        wanted.keySet().forEach(k -> found.put(k, 0));
        RandomSource r = RandomSource.create(3);
        long start = System.nanoTime();
        for (int i = 0; i < 20000; i++) {
            Shape shape = TreeShapes.weepingWillow(r);
            String f = form(joined(logs(shape.cells()), shape.trunkTop()), 1, shape.trunkTop());
            wanted.forEach((name, w) -> found.merge(name, w.equals(f) ? 1 : 0, Integer::sum));
        }
        lines.add(String.format("weeping willow trunks rebuilt by hand, found among 20000 rolls: %s (%.0f s)", found,
                (System.nanoTime() - start) / 1e9));
        return found.values().stream().allMatch(n -> n > 0) ? 0 : 1;
    }

    /** The positions of a shape's logs. */
    private static Set<BlockPos> logs(Map<BlockPos, Shape.Cell> cells) {
        Set<BlockPos> logs = new HashSet<>();
        cells.forEach((p, c) -> {
            if (c.kind() == Shape.Kind.LOG) {
                logs.add(p);
            }
        });
        return logs;
    }

    /** The blocks joined to 0, 0, 0 over faces, edges or corners, up to y top (0, 0, 0 itself included). */
    private static Set<BlockPos> joined(Set<BlockPos> blocks, int top) {
        Set<BlockPos> seen = new HashSet<>(List.of(BlockPos.ZERO));
        List<BlockPos> todo = new ArrayList<>(seen);
        while (!todo.isEmpty()) {
            BlockPos p = todo.removeLast();
            for (BlockPos q : BlockPos.betweenClosed(p.offset(-1, -1, -1), p.offset(1, 1, 1))) {
                if (q.getY() <= top && blocks.contains(q) && seen.add(q.immutable())) {
                    todo.add(q.immutable());
                }
            }
        }
        return seen;
    }

    /** A trunk's layers from..to as text, the same for two trunks that differ only by a turn or a mirror. */
    private static String form(Set<BlockPos> trunk, int from, int to) {
        String best = null;
        for (int k = 0; k < 8; k++) {
            List<int[]> cells = new ArrayList<>();
            for (BlockPos p : trunk) {
                int x = p.getX(), z = p.getZ();
                for (int i = 0; i < k % 4; i++) {
                    int nx = -z;
                    z = x;
                    x = nx;
                }
                if (p.getY() >= from && p.getY() <= to) {
                    cells.add(new int[]{k >= 4 ? -x : x, p.getY(), z});
                }
            }
            int minX = cells.stream().mapToInt(c -> c[0]).min().orElse(0);
            int minZ = cells.stream().mapToInt(c -> c[2]).min().orElse(0);
            String s = cells.stream().sorted(Comparator.<int[]>comparingInt(c -> c[1]).thenComparingInt(c -> c[0])
                            .thenComparingInt(c -> c[2]))
                    .map(c -> (c[0] - minX) + "," + c[1] + "," + (c[2] - minZ))
                    .collect(java.util.stream.Collectors.joining(" "));
            if (best == null || s.compareTo(best) < 0) {
                best = s;
            }
        }
        return best;
    }

    private static boolean isLog(Map<BlockPos, Shape.Cell> cells, BlockPos p) {
        Shape.Cell c = cells.get(p);
        return c != null && c.kind() == Shape.Kind.LOG;
    }

    /**
     * Aries oaks rolled as shapes only, three weeping ones and three without strands: the weeping ones carry many
     * hanging leaves (a leaf under a leaf, with no leaf beside it, not even diagonally), the others few. The weeping
     * ones are written out for concept/render_selftest.py.
     */
    private static int ariesWeeping(Path out, List<String> lines) throws IOException {
        int[] hanging = new int[2];
        for (int w = 0; w < 2; w++) {
            RandomSource r = RandomSource.create(w);
            for (int seed = 0; seed < 3; seed++) {
                Map<BlockPos, Shape.Cell> cells = AriesOak.grow(r, w == 1).cells();
                for (BlockPos p : cells.keySet()) {
                    if (isLeaf(cells, p) && isLeaf(cells, p.above()) && Direction.Plane.HORIZONTAL.stream()
                            .noneMatch(d -> isLeaf(cells, p.relative(d)) || isLeaf(cells, p.relative(d).relative(d.getClockWise())))) {
                        hanging[w]++;
                    }
                }
                if (w == 1) {
                    StringBuilder json = new StringBuilder("[\n");
                    cells.forEach((p, c) -> jsonLine(json, p.getX(), p.getY(), p.getZ(), c.state()));
                    Files.writeString(out.resolve("aries_oak_weeping_" + seed + ".json"), json.append("\n]\n"),
                            StandardCharsets.UTF_8);
                }
            }
        }
        boolean ok = hanging[1] >= 300 && hanging[1] > 5 * hanging[0];
        lines.add(String.format("aries_oak shapes, 3 each: hanging strand leaves without strands %d, weeping %d",
                hanging[0], hanging[1]));
        return ok ? 0 : 1;
    }

    private static boolean isLeaf(Map<BlockPos, Shape.Cell> cells, BlockPos p) {
        Shape.Cell c = cells.get(p);
        return c != null && c.kind() == Shape.Kind.LEAF;
    }

    /**
     * Four saplings in a square grow the species' big tree (large date palm, big weeping willow), taller than
     * minHeight; the four saplings are used up. trunkCorner 1 also wants a 2x2 trunk on the square. The leaves keep
     * their distance, the vines and date clusters hold.
     */
    private static int squareGrowth(ServerLevel level, RandomSource random, Path out, List<String> lines, String name,
                                    int trunkCorner, int minHeight) throws IOException {
        GreekTrees.Species species = species(name);
        int growths = 6, grown = 0, square = 0, minH = 99, maxH = 0, minW = 99, maxW = 0;
        int decayed = 0, mismatched = 0, vines = 0, looseVines = 0, fruit = 0, looseFruit = 0, saplingsLeft = 0, bare = 0;
        int x0 = name.equals("date_palm") ? 1600 : 1900;
        for (int i = 0; i < growths; i++) {
            BlockPos pos = new BlockPos(x0, Y, i * 48);
            platform(level, pos, Blocks.GRASS_BLOCK);
            for (int dx = 0; dx <= 1; dx++) {
                for (int dz = 0; dz <= 1; dz++) {
                    level.setBlockAndUpdate(pos.offset(dx, 0, dz), species.sapling().defaultBlockState());
                }
            }
            grow(level, pos.offset(1, 0, 1), random); // any of the four starts it
            for (int dx = 0; dx <= 1; dx++) {
                for (int dz = 0; dz <= 1; dz++) {
                    if (level.getBlockState(pos.offset(dx, 0, dz)).is(species.sapling())) {
                        saplingsLeft++;
                    }
                }
            }
            if (!level.getBlockState(pos).is(BlockTags.LOGS)) {
                continue;
            }
            grown++;
            boolean all = true;
            for (int dx = 0; dx <= trunkCorner; dx++) {
                for (int dz = 0; dz <= trunkCorner; dz++) {
                    all &= level.getBlockState(pos.offset(dx, 0, dz)).is(BlockTags.LOGS);
                }
            }
            if (all) {
                square++;
            }
            Scan scan = scan(level, pos);
            minH = Math.min(minH, scan.height);
            maxH = Math.max(maxH, scan.height);
            minW = Math.min(minW, scan.width);
            maxW = Math.max(maxW, scan.width);
            Files.writeString(out.resolve("large_" + name + "_" + i + ".json"), scan.json.toString(),
                    StandardCharsets.UTF_8);
            for (BlockPos p : scan.leaves) {
                BlockState before = level.getBlockState(p);
                before.tick(level, p, random);
                BlockState after = level.getBlockState(p);
                if (!after.is(BlockTags.LEAVES)) {
                    decayed++;
                    continue;
                }
                if (!after.getValue(LeavesBlock.DISTANCE).equals(before.getValue(LeavesBlock.DISTANCE))) {
                    mismatched++;
                }
                after.randomTick(level, p, random);
                if (!level.getBlockState(p).is(BlockTags.LEAVES)) {
                    decayed++;
                }
            }
            for (BlockPos p : scan.vines) {
                vines++;
                BlockState v = level.getBlockState(p);
                if (!v.is(Blocks.VINE) || !v.canSurvive(level, p)) {
                    looseVines++;
                }
            }
            for (BlockPos p : scan.fruit) {
                fruit++;
                if (!level.getBlockState(p).canSurvive(level, p)) {
                    looseFruit++;
                }
                if (!level.getBlockState(p.above()).is(Blocks.JUNGLE_LEAVES)) {
                    bare++;
                }
            }
        }
        boolean willow = name.equals("weeping_willow");
        boolean ok = grown == growths && square == growths && saplingsLeft == 0 && minH > minHeight && decayed == 0
                && mismatched == 0 && looseVines == 0 && looseFruit == 0 && bare == 0 && (willow ? vines > 0 : fruit > 0);
        lines.add(String.format("%s from 4 saplings in a square: grown %d/%d, %s %d, saplings left %d, height %d-%d "
                        + "(want over %d), width %d-%d, leaf distance mismatches %d, decayed leaves %d, %s %d, "
                        + "loose %d%s", name, grown, growths, trunkCorner == 1 ? "2x2 trunk" : "trunk on the corner",
                square, saplingsLeft, minH, maxH, minHeight, minW, maxW, mismatched, decayed,
                willow ? "vines" : "date clusters", willow ? vines : fruit, willow ? looseVines : looseFruit,
                willow ? "" : ", without palm leaves right above " + bare));
        return ok ? 0 : 1;
    }

    /** Mulberry trees rolled as shapes only: each carries one kind of mulberry, and all three kinds turn up. */
    private static int mulberryKinds(List<String> lines) {
        int rolls = 300, mixed = 0;
        Map<Block, Integer> trees = new LinkedHashMap<>();
        for (Block twig : GreekTrees.MULBERRY_TWIGS) {
            trees.put(twig, 0);
        }
        for (int seed = 0; seed < rolls; seed++) {
            Set<Block> kinds = new HashSet<>();
            TreeShapes.mulberry(RandomSource.create(seed)).cells().forEach((p, c) -> {
                if (trees.containsKey(c.state().getBlock())) {
                    kinds.add(c.state().getBlock());
                }
            });
            if (kinds.size() > 1) {
                mixed++;
            }
            for (Block k : kinds) {
                trees.merge(k, 1, Integer::sum);
            }
        }
        boolean ok = mixed == 0 && trees.values().stream().allMatch(n -> n > 0);
        lines.add(String.format("mulberry kinds over %d rolls: black/white/red trees %s, trees with more than one "
                + "kind %d", rolls, trees.values().stream().map(String::valueOf).reduce((a, b) -> a + "/" + b)
                .orElse(""), mixed));
        return ok ? 0 : 1;
    }

    /** The mod's own creative tab lists every sapling and every fruit. */
    private static int creativeTab(ServerLevel level, List<String> lines) {
        CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(GreekTrees.id("main"));
        if (tab == null) {
            lines.add("creative tab: MISSING");
            return 1;
        }
        tab.buildContents(new CreativeModeTab.ItemDisplayParameters(level.enabledFeatures(), false, level.registryAccess()));
        List<Item> want = new ArrayList<>(List.of(GreekTrees.GUIDE_BOOK));
        want.addAll(GreekTrees.SPECIES.stream().map(GreekTrees.Species::item).toList());
        for (ItemLike fruit : GreekTrees.FRUIT) {
            want.add(fruit.asItem());
        }
        want.addAll(List.of(GreekTrees.OLIVE_PIT, GreekTrees.SHARPENED_OLIVE_PIT));
        List<Item> shown = tab.getDisplayItems().stream().map(ItemStack::getItem).toList();
        boolean ok = shown.containsAll(want) && shown.size() == want.size();
        ok &= shown.getFirst() == GreekTrees.GUIDE_BOOK;
        lines.add(String.format("creative tab: %d items, guide book first and all saplings and fruit %s, title %s",
                shown.size(), ok, tab.getDisplayName().getString()));
        return ok ? 0 : 1;
    }

    /**
     * The guide book. Recipe: a book and any of the saplings make it, and its recipe-book unlock exists. Gift: given
     * twice to a new player, there is one book. Content: every tree page's recipe (read from the mod's recipe files)
     * makes that tree's sapling from the same ingredients as the server's recipe, the sharpened pit's from two pits
     * on top of each other, and every key the book uses is in both language files.
     */
    private static int guideBook(MinecraftServer server, ServerLevel level, List<String> lines) throws IOException {
        int failures = 0;
        int crafted = 0;
        for (GreekTrees.Species s : GreekTrees.SPECIES) {
            List<ItemStack> stacks = List.of(new ItemStack(Items.BOOK), new ItemStack(s.item()));
            CraftingInput input = CraftingInput.of(2, 1, stacks);
            if (server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)
                    .map(h -> h.value().assemble(input, level.registryAccess())).orElse(ItemStack.EMPTY).is(GreekTrees.GUIDE_BOOK)) {
                crafted++;
            }
        }
        boolean unlock = server.getAdvancements().get(GreekTrees.id("recipes/misc/guide_book")) != null;
        if (crafted != GreekTrees.SPECIES.size() || !unlock) {
            failures++;
        }
        lines.add(String.format("guide book recipe: a book and each sapling give the book %d/%d, unlock advancement %s",
                crafted, GreekTrees.SPECIES.size(), unlock));

        FakePlayer player = FakePlayer.get(level, new GameProfile(UUID.randomUUID(), "guide_book_gift"));
        GuideBook.BOOK.giveOnce(player);
        GuideBook.BOOK.giveOnce(player);
        int books = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            books += stack.is(GreekTrees.GUIDE_BOOK) ? stack.getCount() : 0;
        }
        boolean marked = player.hasAttached(GuideBook.BOOK.gotBook());
        if (books != 1 || !marked) {
            failures++;
        }
        lines.add(String.format("guide book gift: given twice to a new player, books %d (want 1), marked %s", books,
                marked));

        List<Chapter> chapters = GuideBook.BOOK.chapters();
        int trees = 0, matching = 0;
        boolean pits = false;
        List<String> keys = GuideBook.BOOK.keys();
        for (Chapter chapter : chapters) {
            boolean tree = chapter.side() == Chapter.Side.LEFT;
            trees += tree ? 1 : 0;
            for (Section section : chapter.sections()) {
                if (!(section instanceof Section.Recipe recipe)) {
                    continue;
                }
                List<ItemStack> g = recipe.grid();
                if (tree) { // its icon is its sapling
                    List<Item> in = g.stream().filter(x -> !x.isEmpty()).map(ItemStack::getItem)
                            .sorted(Comparator.comparing(Item::toString)).toList();
                    String name = BuiltInRegistries.ITEM.getKey(chapter.icon().getItem()).getPath().replace("_sapling", "");
                    List<Item> want = RECIPES.get(name).stream().sorted(Comparator.comparing(Item::toString)).toList();
                    if (recipe.result().is(chapter.icon().getItem()) && in.equals(want)) {
                        matching++;
                    }
                } else {
                    pits = recipe.result().is(GreekTrees.SHARPENED_OLIVE_PIT) && g.stream().filter(x -> !x.isEmpty()).count() == 2
                            && g.get(1).is(GreekTrees.OLIVE_PIT) && g.get(4).is(GreekTrees.OLIVE_PIT);
                }
            }
        }
        List<String> missing = GuideBook.BOOK.missingKeys("en_us", "de_de");
        if (trees != GreekTrees.SPECIES.size() || matching != trees || !pits || !missing.isEmpty()) {
            failures++;
        }
        lines.add(String.format("guide book content: %d chapters, tree chapters %d, recipes as the server's %d/%d, sharpened "
                        + "pit recipe %s, keys %d, missing in the language files %s", chapters.size(), trees, matching,
                trees, pits, keys.size(), missing.isEmpty() ? "none" : missing));
        return failures;
    }

    private static GreekTrees.Species species(String name) {
        return GreekTrees.SPECIES.stream().filter(x -> x.name().equals(name)).findFirst().orElseThrow();
    }

    private static int age(BlockState state) {
        IntegerProperty age = state.hasProperty(CocoaBlock.AGE) ? CocoaBlock.AGE : HangingFruitBlock.STAGE;
        return state.getValue(age);
    }

    /** A right click on a block face by a (fake) player holding stack, the way the server handles a real one. */
    private static void rightClick(ServerLevel level, BlockPos pos, Direction face, ItemStack stack) {
        FakePlayer player = FakePlayer.get(level);
        player.setPos(pos.getX() + 0.5, pos.getY() - 1.5, pos.getZ() + 2.5);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        Vec3 hit = Vec3.atCenterOf(pos).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        player.gameMode.useItemOn(player, level, stack, InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, pos, false));
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    /** What a fruit block drops at its two unripe stages, if both give the same; -1 otherwise. */
    private static int unripeDrops(ServerLevel level, BlockState ripe, IntegerProperty age, BlockPos pos, Item item) {
        int[] green = dropRange(level, ripe.setValue(age, 0), pos, item);
        int[] half = dropRange(level, ripe.setValue(age, 1), pos, item);
        return green[0] == green[1] && half[0] == half[1] && green[0] == half[0] ? green[0] : -1;
    }

    /** Fewest and most of the item a block drops when broken, over 50 tries. */
    private static int[] dropRange(ServerLevel level, BlockState state, BlockPos pos, Item item) {
        int min = 99, max = 0;
        for (int i = 0; i < 50; i++) {
            int n = count(Block.getDrops(state, level, pos, null), item);
            min = Math.min(min, n);
            max = Math.max(max, n);
        }
        return new int[]{min, max};
    }

    private static int count(List<ItemStack> drops, Item item) {
        return drops.stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    /** Two vanilla growth steps: stage 0 -> 1, then the tree (what random ticks and bone meal call). */
    private static void grow(ServerLevel level, BlockPos pos, RandomSource random) {
        for (int step = 0; step < 2; step++) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof SaplingBlock sapling) {
                sapling.advanceTree(level, pos, state, random);
            }
        }
    }

    private static void platform(ServerLevel level, BlockPos pos, Block ground) {
        for (int dx = -9; dx <= 9; dx++) {
            for (int dz = -9; dz <= 9; dz++) {
                level.setBlockAndUpdate(pos.offset(dx, -1, dz), ground.defaultBlockState());
            }
        }
    }

    private record Scan(int height, int width, int logs, List<BlockPos> leaves, List<BlockPos> fruit,
                        List<BlockPos> vines, List<BlockPos> others, Map<Block, Integer> counts, StringBuilder json) {
    }

    private static Scan scan(ServerLevel level, BlockPos pos) {
        return scan(level, pos, 16, 40);
    }

    /** Everything but air from pos out to reach blocks and up to maxY; the JSON is what concept/ renders. */
    private static Scan scan(ServerLevel level, BlockPos pos, int reach, int maxY) {
        int height = 0, logs = 0, minX = 99, maxX = -99, minZ = 99, maxZ = -99;
        List<BlockPos> leaves = new ArrayList<>();
        List<BlockPos> fruit = new ArrayList<>();
        List<BlockPos> vines = new ArrayList<>();
        List<BlockPos> others = new ArrayList<>();
        Map<Block, Integer> counts = new LinkedHashMap<>();
        StringBuilder json = new StringBuilder("[\n");
        for (int dy = 0; dy <= maxY; dy++) {
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    BlockPos p = pos.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(p);
                    if (state.isAir()) {
                        continue;
                    }
                    height = Math.max(height, dy + 1);
                    counts.merge(state.getBlock(), 1, Integer::sum);
                    minX = Math.min(minX, dx);
                    maxX = Math.max(maxX, dx);
                    minZ = Math.min(minZ, dz);
                    maxZ = Math.max(maxZ, dz);
                    if (state.is(BlockTags.LOGS)) {
                        logs++;
                    } else if (state.is(BlockTags.LEAVES)) {
                        leaves.add(p);
                    } else if (state.is(GreekTrees.DATE_CLUSTER) || state.getBlock() instanceof HangingFruitBlock) {
                        fruit.add(p);
                    } else if (state.is(Blocks.VINE)) {
                        vines.add(p);
                    } else {
                        others.add(p);
                    }
                    jsonLine(json, dx, dy, dz, state);
                }
            }
        }
        json.append("\n]\n");
        int width = Math.max(maxX - minX, maxZ - minZ) + 1;
        return new Scan(height, width, logs, leaves, fruit, vines, others, counts, json);
    }

    /** One block for concept/: x, y, z, the block and its axis (or how a slab lies). */
    private static void jsonLine(StringBuilder json, int x, int y, int z, BlockState state) {
        String axis = state.hasProperty(BlockStateProperties.AXIS)
                ? state.getValue(BlockStateProperties.AXIS).getSerializedName() : "y";
        if (state.getBlock() instanceof SlabBlock) {
            axis = state.getValue(SlabBlock.TYPE).getSerializedName();
        }
        if (json.length() > 2) {
            json.append(",\n");
        }
        json.append(String.format("[%d,%d,%d,\"%s\",\"%s\"]", x, y, z,
                BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath(), axis));
    }
}
