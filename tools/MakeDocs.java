import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * The README's pictures, made from the guide book's: docs/trees/&lt;tree&gt;.png (the book's tree pictures as they
 * are), docs/trees.png (the eight trees in a row on the book's page colour) and, given a book shot of
 * {@code ./gradlew runClient -PbookShots} (1280 x 720, GUI scale 3), docs/book.png: the book cut out of it.
 * Run: java tools/MakeDocs.java [run/screenshots/book_greektrees_en_us_04.png] (from the project folder).
 */
public class MakeDocs {
    static final Path PICTURES = Path.of("src/main/resources/assets/greektrees/textures/gui/book");
    static final String[] TREES = {"cypress", "olive", "fig", "strawberry_tree", "date_palm", "mulberry",
            "weeping_willow", "aries_oak"};
    static final int PAGE = 0xFFF4F1EA, SIZE = 192, GAP = 8;

    public static void main(String[] args) throws IOException {
        Files.createDirectories(Path.of("docs/trees"));
        BufferedImage row = new BufferedImage(GAP + TREES.length * (SIZE + GAP), SIZE + 2 * GAP, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = row.createGraphics();
        g.setColor(new java.awt.Color(PAGE, true));
        g.fillRect(0, 0, row.getWidth(), row.getHeight());
        for (int i = 0; i < TREES.length; i++) {
            g.drawImage(ImageIO.read(PICTURES.resolve(TREES[i] + ".png").toFile()), GAP + i * (SIZE + GAP), GAP, null);
        }
        g.dispose();
        ImageIO.write(row, "png", new File("docs/trees.png"));
        try (var pictures = Files.newDirectoryStream(PICTURES, "*.png")) {
            for (Path picture : pictures) {
                Files.copy(picture, Path.of("docs/trees").resolve(picture.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        if (args.length > 0) { // panel 232 x 230 with its tabs (23 out on each side), a pixel of air around
            BufferedImage shot = ImageIO.read(new File(args[0]));
            int left = (shot.getWidth() / 3 - 232) / 2, top = Math.max(2, (shot.getHeight() / 3 - 230) / 2);
            ImageIO.write(shot.getSubimage((left - 24) * 3, (top - 1) * 3, (232 + 48) * 3, 232 * 3), "png",
                    new File("docs/book.png"));
        }
    }
}
