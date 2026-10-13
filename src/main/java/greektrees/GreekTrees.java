package greektrees;

import bcf.guidebook.GuideBookItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import greektrees.tree.AriesOak;
import greektrees.tree.GreekTreeFeature;
import greektrees.tree.Shape;
import greektrees.tree.TreeShapes;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.registry.CompostingChanceRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.NestedLootTable;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

public final class GreekTrees implements ModInitializer {
    public static final String MOD_ID = "greektrees";

    /** One tree: its sapling, the potted sapling and the sapling item. */
    public record Species(String name, Block sapling, Block potted, Item item) {
    }

    public static final List<Species> SPECIES = new ArrayList<>();

    // Fruit hangs on the trees and is harvested with a right click once ripe (see HangingFruitBlock). The fruit items
    // are food and plant: olives, figs, arbutus berries and mulberries under leaves, dates on the side of jungle wood.
    /** Dates hanging from the side of a palm trunk; cocoa behaviour, see {@link DateClusterBlock}. */
    public static final Block DATE_CLUSTER = block("date_cluster", DateClusterBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).randomTicks().strength(0.2f, 3.0f)
                    .sound(SoundType.WOOD).noOcclusion().pushReaction(PushReaction.DESTROY));
    public static final Item DATE = item("date", props -> new ItemNameBlockItem(DATE_CLUSTER, props
            .food(new FoodProperties.Builder().nutrition(3).saturationModifier(0.3f).build())));

    /** Left over from eating an olive; thrown like a snowball, it hurts for 1.5 hearts. */
    public static final Item OLIVE_PIT = item("olive_pit", OlivePitItem::new);
    /** Two pits on top of each other in the crafting grid; thrown it hurts for 3.5 hearts. */
    public static final Item SHARPENED_OLIVE_PIT = item("sharpened_olive_pit", OlivePitItem::new);
    /** Olives hanging under the leaves of the olive tree. */
    public static final Block OLIVE_TWIG = block("olive_twig", HangingFruitBlock::new, fruitTwig(MapColor.COLOR_PURPLE));
    /** Edible, gives its pit back. */
    public static final Item OLIVE = item("olive", props -> new FruitItem(OLIVE_TWIG, props
            .food(new FoodProperties.Builder().nutrition(2).saturationModifier(0.4f).usingConvertsTo(OLIVE_PIT)
                    .build())));
    /** Figs hanging under the leaves of the fig tree. */
    public static final Block FIG_TWIG = block("fig_twig", HangingFruitBlock::new, fruitTwig(MapColor.COLOR_PURPLE));
    public static final Item FIG = item("fig", props -> new FruitItem(FIG_TWIG, props
            .food(new FoodProperties.Builder().nutrition(3).saturationModifier(0.4f).build())));
    /** Arbutus berries hanging under the leaves of the strawberry tree. */
    public static final Block ARBUTUS_TWIG = block("arbutus_twig", HangingFruitBlock::new, fruitTwig(MapColor.COLOR_RED));
    public static final Item ARBUTUS_BERRY = item("arbutus_berry", props -> new FruitItem(ARBUTUS_TWIG, props
            .food(new FoodProperties.Builder().nutrition(3).saturationModifier(0.2f).build())));

    // Mulberries hanging under the leaves of the mulberry tree, one kind per tree. All start white-green; black ones
    // turn red, then black; white ones cream, then white; red ones pink, then deep red.
    public static final Block BLACK_MULBERRY_TWIG = block("black_mulberry_twig", HangingFruitBlock::new,
            fruitTwig(MapColor.COLOR_BLACK));
    public static final Item BLACK_MULBERRY = item("black_mulberry",
            props -> new FruitItem(BLACK_MULBERRY_TWIG, mulberry(props)));
    public static final Block WHITE_MULBERRY_TWIG = block("white_mulberry_twig", HangingFruitBlock::new,
            fruitTwig(MapColor.SNOW));
    public static final Item WHITE_MULBERRY = item("white_mulberry",
            props -> new FruitItem(WHITE_MULBERRY_TWIG, mulberry(props)));
    public static final Block RED_MULBERRY_TWIG = block("red_mulberry_twig", HangingFruitBlock::new,
            fruitTwig(MapColor.COLOR_RED));
    public static final Item RED_MULBERRY = item("red_mulberry",
            props -> new FruitItem(RED_MULBERRY_TWIG, mulberry(props)));
    /** The three kinds of mulberry tree, by their twigs. */
    public static final Block[] MULBERRY_TWIGS = {BLACK_MULBERRY_TWIG, WHITE_MULBERRY_TWIG, RED_MULBERRY_TWIG};

    /** Every tree in one book; crafted from a book and any of the saplings, and given once to every player. */
    public static final Item GUIDE_BOOK = item("guide_book", props -> new GuideBookItem(props.stacksTo(1), GuideBook.BOOK));

    /** The fruit in the order of the trees. */
    public static final ItemLike[] FRUIT = {OLIVE, FIG, ARBUTUS_BERRY, DATE, BLACK_MULBERRY, WHITE_MULBERRY,
            RED_MULBERRY};

    public static final EntityType<ThrownOlivePit> OLIVE_PIT_ENTITY = Registry.register(BuiltInRegistries.ENTITY_TYPE,
            id("olive_pit"), EntityType.Builder.<ThrownOlivePit>of(ThrownOlivePit::new, MobCategory.MISC)
                    .sized(0.25f, 0.25f).clientTrackingRange(4).updateInterval(10)
                    .build("olive_pit"));

    public static final LootItemConditionType NEAR_BLOCK = Registry.register(BuiltInRegistries.LOOT_CONDITION_TYPE,
            id("near_block"), new LootItemConditionType(NearBlock.CODEC));

    /** Extra pools for vanilla leaves: fruit now and then, but only near the wood a Greek tree is built from. */
    public static final ResourceKey<LootTable> PALM_LEAF_DATES = lootTable("blocks/palm_leaf_dates");
    public static final ResourceKey<LootTable> OLIVE_LEAF_OLIVES = lootTable("blocks/olive_leaf_olives");
    public static final ResourceKey<LootTable> FIG_LEAF_FIGS = lootTable("blocks/fig_leaf_figs");
    public static final ResourceKey<LootTable> STRAWBERRY_TREE_LEAF_BERRIES = lootTable("blocks/strawberry_tree_leaf_berries");
    public static final ResourceKey<LootTable> BLACK_MULBERRY_LEAF_MULBERRIES = lootTable("blocks/black_mulberry_leaf_mulberries");
    public static final ResourceKey<LootTable> WHITE_MULBERRY_LEAF_MULBERRIES = lootTable("blocks/white_mulberry_leaf_mulberries");
    public static final ResourceKey<LootTable> RED_MULBERRY_LEAF_MULBERRIES = lootTable("blocks/red_mulberry_leaf_mulberries");

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        register("cypress", TreeShapes::cypress, false);
        register("olive", TreeShapes::olive, false);
        register("fig", TreeShapes::fig, false);
        register("strawberry_tree", TreeShapes::strawberryTree, false);
        register("date_palm", TreeShapes::datePalm, TreeShapes::largeDatePalm, true);
        register("mulberry", TreeShapes::mulberry, false);
        register("weeping_willow", TreeShapes::weepingWillow, TreeShapes::largeWeepingWillow, false);
        // the Aries oak only grows from sixteen saplings in a 4x4 square
        Registry.register(BuiltInRegistries.FEATURE, AriesOakSaplingBlock.TREE.location(),
                new GreekTreeFeature(AriesOak::grow));
        registerSapling("aries_oak", AriesOakSaplingBlock::new);

        // olives and figs both grow under azalea leaves, dates and mulberries under jungle leaves: one pool each
        // (a mulberry pool only drops next to its own kind of twig)
        Map<Block, List<ResourceKey<LootTable>>> leafFruit = Map.of(
                Blocks.JUNGLE_LEAVES, List.of(PALM_LEAF_DATES, BLACK_MULBERRY_LEAF_MULBERRIES,
                        WHITE_MULBERRY_LEAF_MULBERRIES, RED_MULBERRY_LEAF_MULBERRIES),
                Blocks.AZALEA_LEAVES, List.of(OLIVE_LEAF_OLIVES, FIG_LEAF_FIGS),
                Blocks.MANGROVE_LEAVES, List.of(STRAWBERRY_TREE_LEAF_BERRIES));
        LootTableEvents.MODIFY.register((key, table, source, registries) -> {
            if (!source.isBuiltin()) {
                return;
            }
            leafFruit.forEach((leaves, fruits) -> {
                if (leaves.getLootTable().equals(key)) {
                    for (ResourceKey<LootTable> fruit : fruits) {
                        table.withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1))
                                .add(NestedLootTable.lootTableReference(fruit)));
                    }
                }
            });
        });

        ItemLike[] saplings = SPECIES.stream().map(Species::item).toArray(ItemLike[]::new);
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, id("main"), FabricItemGroup.builder()
                .icon(() -> new ItemStack(SPECIES.get(1).item()))
                .title(Component.translatable("itemGroup.greektrees"))
                .displayItems((params, out) -> {
                    out.accept(GUIDE_BOOK);
                    for (ItemLike sapling : saplings) {
                        out.accept(sapling);
                    }
                    for (ItemLike fruit : FRUIT) {
                        out.accept(fruit);
                    }
                    out.accept(OLIVE_PIT);
                    out.accept(SHARPENED_OLIVE_PIT);
                })
                .build());
        ItemGroupEvents.modifyEntriesEvent(vanillaTab("natural_blocks"))
                .register(out -> out.addAfter(Items.CHERRY_SAPLING, saplings));
        ItemGroupEvents.modifyEntriesEvent(vanillaTab("food_and_drinks"))
                .register(out -> out.addAfter(Items.GLOW_BERRIES, FRUIT));
        ItemGroupEvents.modifyEntriesEvent(vanillaTab("combat"))
                .register(out -> out.addAfter(Items.SNOWBALL, OLIVE_PIT, SHARPENED_OLIVE_PIT));

        DispenserBlock.registerProjectileBehavior(OLIVE_PIT);
        DispenserBlock.registerProjectileBehavior(SHARPENED_OLIVE_PIT);
        for (Species s : SPECIES) {
            CompostingChanceRegistry.INSTANCE.add(s.item(), 0.3f); // same chance as vanilla saplings
        }
        for (Item fruit : new Item[]{OLIVE, FIG, ARBUTUS_BERRY, DATE, BLACK_MULBERRY, WHITE_MULBERRY, RED_MULBERRY,
                OLIVE_PIT}) {
            CompostingChanceRegistry.INSTANCE.add(fruit, 0.3f); // like sweet berries and seeds
        }
        GuideBook.BOOK.giveOnJoin();
        DevSelfTest.register();
    }

    /**
     * Feature greektrees:&lt;name&gt; (its configured feature comes from data/greektrees/worldgen), a tree grower
     * pointing at it, the sapling, the potted sapling and the item.
     */
    private static void register(String name, Function<RandomSource, Shape> shapes, boolean growsOnSand) {
        register(name, shapes, null, growsOnSand);
    }

    /**
     * Like {@link #register(String, Function, boolean)}, with a big tree (feature greektrees:large_&lt;name&gt;)
     * that four saplings in a square grow into, the way vanilla grows a dark oak.
     */
    private static void register(String name, Function<RandomSource, Shape> shapes,
                                 Function<RandomSource, Shape> squareShapes, boolean growsOnSand) {
        ResourceLocation treeId = id(name);
        Registry.register(BuiltInRegistries.FEATURE, treeId, new GreekTreeFeature(shapes));
        ResourceKey<ConfiguredFeature<?, ?>> tree = ResourceKey.create(Registries.CONFIGURED_FEATURE, treeId);
        Optional<ResourceKey<ConfiguredFeature<?, ?>>> square = Optional.empty();
        if (squareShapes != null) {
            ResourceLocation squareId = id("large_" + name);
            Registry.register(BuiltInRegistries.FEATURE, squareId, new GreekTreeFeature(squareShapes));
            square = Optional.of(ResourceKey.create(Registries.CONFIGURED_FEATURE, squareId));
        }
        TreeGrower grower = new TreeGrower(treeId.toString(), square, Optional.of(tree), Optional.empty());
        registerSapling(name, p -> new GreekSaplingBlock(grower, growsOnSand, p));
    }

    /** The sapling greektrees:&lt;name&gt;_sapling, its potted form and its item. */
    private static void registerSapling(String name, Function<BlockBehaviour.Properties, Block> factory) {
        String saplingName = name + "_sapling";
        Block sapling = block(saplingName, factory,
                BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollission().randomTicks().instabreak()
                        .sound(SoundType.GRASS).pushReaction(PushReaction.DESTROY));
        Block potted = block("potted_" + saplingName, p -> new FlowerPotBlock(sapling, p),
                BlockBehaviour.Properties.of().instabreak().noOcclusion().pushReaction(PushReaction.DESTROY));
        Item item = Registry.register(BuiltInRegistries.ITEM, id(saplingName),
                new BlockItem(sapling, new Item.Properties()));
        SPECIES.add(new Species(name, sapling, potted, item));
    }

    /** All three kinds of mulberry feed the same. */
    private static Item.Properties mulberry(Item.Properties props) {
        return props.food(new FoodProperties.Builder().nutrition(2).saturationModifier(0.3f).build());
    }

    private static BlockBehaviour.Properties fruitTwig(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).noCollission().randomTicks().instabreak()
                .sound(SoundType.SWEET_BERRY_BUSH).pushReaction(PushReaction.DESTROY);
    }

    private static ResourceKey<CreativeModeTab> vanillaTab(String name) {
        return ResourceKey.create(Registries.CREATIVE_MODE_TAB, ResourceLocation.withDefaultNamespace(name));
    }

    private static ResourceKey<LootTable> lootTable(String path) {
        return ResourceKey.create(Registries.LOOT_TABLE, id(path));
    }

    private static Item item(String name, Function<Item.Properties, Item> factory) {
        return Registry.register(BuiltInRegistries.ITEM, id(name), factory.apply(new Item.Properties()));
    }

    private static Block block(String name, Function<BlockBehaviour.Properties, Block> factory,
                               BlockBehaviour.Properties props) {
        return Registry.register(BuiltInRegistries.BLOCK, id(name), factory.apply(props));
    }
}
