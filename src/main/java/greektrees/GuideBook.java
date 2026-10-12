package greektrees;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import bcf.guidebook.Book;
import bcf.guidebook.BookTheme;
import bcf.guidebook.Chapter;
import bcf.guidebook.Section;
import bcf.guidebook.Section.Break;
import bcf.guidebook.Section.Heading;
import bcf.guidebook.Section.ItemGrid;
import bcf.guidebook.Section.ItemLine;
import bcf.guidebook.Section.Note;
import bcf.guidebook.Section.Pictures;
import bcf.guidebook.Section.Quote;
import bcf.guidebook.Section.Recipe;
import bcf.guidebook.Section.Text;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

/**
 * The content of the guide book, as data: chapters of sections, which the screen of bcf-guide-book-lib wraps and
 * breaks into pages. Texts are translation keys {@code greektrees.book.<key>} (written by tools/make_book.py); the
 * self-test checks every key the book uses against both language files.
 */
public final class GuideBook {
    /** A blue panel with one marble-white page. */
    public static final Book BOOK = Book.of(GreekTrees.MOD_ID, BookTheme.builder()
            .panel(0xFF3F74B8, 0xFF9CC3EC, 0xFF234A80).tabs(0xFF1B3B6B, 0xFF2F5C99)
            .page(0xFFF4F1EA, 0xFFB9C4D2).ink(0xFF1A3C78, 0xFF2C60AA, 0xFF1E2430, 0xFF5A6478)
            .slots(0xFFE2E0D8, 0xFF9AA3B0, 0xFF5F7FA8)
            .buttons(0xFF2C60AA, 0xFF4A86D8, 0xFF6B7F9A, 0xFF14305C, 0xFF8FBBEF, 0xFF234A80, 0xFFFFFFFF, 0xFFC6D0DE)
            .build(), GuideBook::chapters);

    /** Trees with a planting text of their own instead of {@code plant.default}. */
    private static final Set<String> OWN_PLANTING = Set.of("date_palm", "weeping_willow", "aries_oak");

    private GuideBook() {
    }

    public static String key(String name) {
        return BOOK.key(name);
    }

    /** Built when the book opens: item stacks need the registries. */
    public static List<Chapter> chapters() {
        List<Chapter> chapters = new ArrayList<>();
        chapters.add(new Chapter(key("basics"), new ItemStack(GreekTrees.GUIDE_BOOK), Chapter.Side.RIGHT,
                List.of(new Text(key("basics.text")))));
        Map<String, List<ItemLike>> fruit = Map.of("olive", List.of(GreekTrees.OLIVE), "fig", List.of(GreekTrees.FIG),
                "strawberry_tree", List.of(GreekTrees.ARBUTUS_BERRY), "date_palm", List.of(GreekTrees.DATE),
                "mulberry", List.of(GreekTrees.BLACK_MULBERRY, GreekTrees.WHITE_MULBERRY, GreekTrees.RED_MULBERRY));
        List<Section> rows = new ArrayList<>();
        for (GreekTrees.Species s : GreekTrees.SPECIES) {
            String name = s.name();
            List<ItemStack> fruits = fruit.getOrDefault(name, List.of()).stream().map(ItemStack::new).toList();
            List<Section> sections = new ArrayList<>(List.of(picture(name), new Text(key(name + ".about")),
                    new Quote(key(name + ".lore")), new Heading(key("heading.crafting")), recipe(name + "_sapling"),
                    new Heading(key("heading.planting"))));
            String planting = key(OWN_PLANTING.contains(name) ? name + ".plant" : "plant.default");
            if (name.equals("aries_oak")) { // grows only from a 4x4 square of saplings, and bears no fruit
                sections.add(3, new Note(key(name + ".dedication"))); // under the lore
                sections.add(new ItemGrid(new ItemStack(s.item()), 4, List.of(planting)));
            } else {
                sections.add(new Text(planting));
                sections.add(new Heading(key("heading.fruit")));
                sections.add(fruits.isEmpty() ? new Text(key("fruit.none")) : new ItemLine(fruits, key(name + ".fruit")));
            }
            chapters.add(new Chapter(key(name + ".name"), new ItemStack(s.item()), Chapter.Side.LEFT, sections));
            for (ItemStack f : fruits) {
                FoodProperties food = f.get(DataComponents.FOOD);
                rows.add(new ItemLine(List.of(f), key("fruit.row"), f.getHoverName(), food == null ? 0 : food.nutrition(),
                        Component.translatable(key(name + ".name"))));
            }
        }

        Item palm = GreekTrees.SPECIES.get(4).item(), willow = GreekTrees.SPECIES.get(6).item();
        List<Section> big = new ArrayList<>();
        for (Map.Entry<String, Item> tree : List.of(Map.entry("large_date_palm", palm), Map.entry("large_weeping_willow", willow))) {
            big.add(new Heading(key(tree.getKey() + ".name")));
            big.add(picture(tree.getKey()));
            big.add(new ItemGrid(new ItemStack(tree.getValue()), 2, List.of(key(tree.getKey() + ".about"), key("big.square"))));
        }
        chapters.add(new Chapter(key("big"), new ItemStack(palm), Chapter.Side.RIGHT, big));

        List<Section> fruits = new ArrayList<>(List.of(new Text(key("fruit.text")),
                new Pictures(List.of(stage(0), stage(1), stage(2)), 32, 16, false), new Break()));
        fruits.addAll(rows);
        chapters.add(new Chapter(key("fruit"), new ItemStack(GreekTrees.OLIVE), Chapter.Side.RIGHT, fruits));

        chapters.add(new Chapter(key("pits"), new ItemStack(GreekTrees.OLIVE_PIT), Chapter.Side.RIGHT, List.of(
                new Text(key("pits.text")), new Heading(key("heading.crafting")), recipe("sharpened_olive_pit"))));
        return chapters;
    }

    private static Pictures picture(String name) {
        return BOOK.picture(name, 96, 192, true);
    }

    private static Identifier stage(int stage) {
        return GreekTrees.id("textures/block/olive_twig_stage" + stage + ".png");
    }

    private static Recipe recipe(String name) {
        return BOOK.recipe(name);
    }
}
