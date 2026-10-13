"""The guide book (PLAN-BOOK.md): its textures, its JSON and its texts.

python tools/make_book.py -> assets/greektrees/textures/gui/book/ (the tree pictures), the item texture and the
preview sheets art/book_concepts/preview_item.png and preview_pictures.png. The tree pictures come from the picked growths kept in
art/book_concepts/trees/ (every self-test rolls new ones).
python tools/make_book.py choose -> art/book_concepts/preview_tree_pictures_all.png, the first four growths of the
last self-test per tree to pick from; copy the picked JSON to art/book_concepts/trees/ and set PICK.
make_resources.py calls write_all() for the item model, the recipe, its unlock and the book texts.
"""
import io
import os
import sys
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from make_fruits import item  # noqa: E402
from recipes import ingredient, recipe_unlock  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'greektrees')
GUI = os.path.join(ASSETS, 'textures', 'gui', 'book')
ART = os.path.join(ROOT, 'art', 'book_concepts')
NS = 'greektrees'

TREES = ['cypress', 'olive', 'fig', 'strawberry_tree', 'date_palm', 'mulberry', 'weeping_willow', 'aries_oak']
PICTURES = TREES + ['large_date_palm', 'large_weeping_willow']
# which growth each picture shows: art/book_concepts/trees/<name>_<n>.json, copied from the self-test of 2026-10-03
PICK = {'cypress': 3, 'olive': 0, 'fig': 2, 'strawberry_tree': 0, 'date_palm': 1, 'mulberry': 2,
        'weeping_willow': 1, 'aries_oak': 1, 'large_date_palm': 0, 'large_weeping_willow': 0}
PIC = 192  # tree picture in pixels, drawn on 96 x 96 GUI pixels (112 left too little room for the text)
BOOK_ITEM = 2  # the item texture variant (1-2, see item_texture)

PAGE = (244, 241, 234, 255)  # the book's page (the theme in GuideBook.java), for the preview sheet only
FRAME = (185, 196, 210, 255)
CREAM, BLUE, DBLUE = (240, 232, 212), (44, 96, 170), (26, 60, 120)


# ================================================================ JSON and texts (called from make_resources.py)

def write_all(write):
    item(write, 'guide_book')
    ingredients = ['minecraft:book', f'#{NS}:saplings']  # the tag is written by make_resources.py
    write(f'data/{NS}/recipe/guide_book.json', {
        'type': 'minecraft:crafting_shapeless', 'category': 'misc',
        'ingredients': [ingredient(i) for i in ingredients], 'result': {'id': f'{NS}:guide_book'}})
    recipe_unlock(write, f'{NS}:guide_book', ingredients)
    lang = {f'item.{NS}.guide_book': ('Greek Trees Guide', 'Baumkunde: Griechische Bäume')}
    for key, texts in TEXTS.items():
        lang[f'{NS}.book.{key}'] = texts
    return lang


# greektrees.book.<key>: (English, German). <tree>.about ends with <tree>.lore, set in italics.
TEXTS = {
    'basics': ('Basics', 'Grundlagen'),
    'basics.text': ('Every tree grows from its own sapling and is built from vanilla blocks; no two grow alike. '
                    'Saplings can be potted and composted. A tree only grows where all of its wood fits.',
                    'Jeder Baum wächst aus seinem eigenen Setzling und besteht aus Vanilla-Blöcken; keine zwei '
                    'wachsen gleich. Setzlinge lassen sich eintopfen und kompostieren. Ein Baum wächst nur, wo sein '
                    'ganzes Holz Platz hat.'),
    'heading.crafting': ('Crafting', 'Herstellung'),
    'heading.planting': ('Planting', 'Pflanzen'),
    'heading.fruit': ('Fruit', 'Früchte'),
    'plant.default': ('Plant on dirt or grass. Grows with light or bone meal.',
                      'Auf Erde oder Gras. Wächst mit Licht oder Knochenmehl.'),
    'fruit.none': ('Bears no fruit.', 'Trägt keine Früchte.'),

    'cypress.name': ('Cypress', 'Zypresse'),
    'cypress.about': ('Tall, narrow column, 21–23 high. Acacia wood, azalea leaves.',
                      'Hohe, schmale Säule, 21–23 hoch. Akazienholz, Azaleenlaub.'),
    'cypress.lore': ('Named after Kyparissos, whom Apollo turned into the tree of mourning.',
                     'Benannt nach Kyparissos, den Apollon in den Baum der Trauer verwandelte.'),

    'olive.name': ('Olive Tree', 'Olivenbaum'),
    'olive.about': ('Short forked trunk with roots, crowns at different heights, 8–12 high. Oak wood, azalea leaves.',
                    'Kurzer, gegabelter Stamm mit Wurzeln, Kronen auf mehreren Höhen, 8–12 hoch. Eichenholz, '
                    'Azaleenlaub.'),
    'olive.lore': ("Athena's gift to Athens.", 'Athenes Geschenk an Athen.'),
    'olive.fruit': ('Olives under the leaves. 2 hunger; eating one leaves a pit.',
                    'Oliven unter dem Laub. 2 Hunger; übrig bleibt ein Kern.'),

    'fig.name': ('Fig Tree', 'Feigenbaum'),
    'fig.about': ('Low and wide, several stems, 7 high. Acacia wood, azalea leaves.',
                  'Niedrig und breit, mehrere Stämme, 7 hoch. Akazienholz, Azaleenlaub.'),
    'fig.lore': ('Figs fed the first athletes of Olympia.', 'Feigen nährten die ersten Athleten von Olympia.'),
    'fig.fruit': ('Figs under the leaves. 3 hunger.', 'Feigen unter dem Laub. 3 Hunger.'),

    'strawberry_tree.name': ('Strawberry Tree', 'Erdbeerbaum'),
    'strawberry_tree.about': ('Thin orange-red stems, open dark crown, 10–11 high. Stripped acacia wood, mangrove '
                              'leaves.',
                              'Dünne, orangerote Stämme, lichte dunkle Krone, 10–11 hoch. Entrindetes Akazienholz, '
                              'Mangrovenlaub.'),
    'strawberry_tree.lore': ('Its berries take a year to ripen, so flowers and fruit hang side by side.',
                             'Die Früchte reifen ein Jahr, darum hängen Blüten und Früchte nebeneinander.'),
    'strawberry_tree.fruit': ('Arbutus berries under the leaves. 3 hunger.',
                              'Erdbeerbaumfrüchte unter dem Laub. 3 Hunger.'),

    'date_palm.name': ('Cretan Date Palm', 'Kretische Dattelpalme'),
    'date_palm.about': ('One to three slim, leaning trunks, 15–18 high. Jungle wood, jungle leaves.',
                        'Ein bis drei schlanke, geneigte Stämme, 15–18 hoch. Tropenholz, Tropenlaub.'),
    'date_palm.lore': ('The Cretan date palm, named after Theophrastus, grows wild on the beach of Vai.',
                       'Die Kretische Dattelpalme, benannt nach Theophrast, wächst wild am Strand von Vai.'),
    'date_palm.plant': ('On dirt, grass or sand. Four in a square grow a large palm.',
                        'Auf Erde, Gras oder Sand. Vier im Quadrat ergeben eine große Palme.'),
    'date_palm.fruit': ('Dates on the trunk, right under the crown. 3 hunger.',
                        'Datteln am Stamm, direkt unter der Krone. 3 Hunger.'),

    'mulberry.name': ('Mulberry Tree', 'Maulbeerbaum'),
    'mulberry.about': ('Short thick trunk under a dense round dome, 10–11 high. Acacia wood, jungle leaves.',
                       'Kurzer, dicker Stamm unter dichter, runder Krone, 10–11 hoch. Akazienholz, Tropenlaub.'),
    'mulberry.lore': ('Once white, the berries turned dark with the blood of Pyramus.',
                      'Einst weiß, färbte das Blut des Pyramus die Beeren dunkel.'),
    'mulberry.fruit': ('Black, white or red mulberries, one kind per tree. 2 hunger.',
                       'Schwarze, weiße oder rote Maulbeeren, je Baum eine Sorte. 2 Hunger.'),

    'weeping_willow.name': ('Weeping Willow', 'Trauerweide'),
    'weeping_willow.about': ('Softly bent trunk, a curtain of leaf strands with room inside, 11–21 high. '
                             'Acacia wood, mangrove leaves, vines.',
                             'Sanft gebogener Stamm, ein Vorhang aus Laubsträngen mit Raum darunter, 11–21 hoch. '
                             'Akazienholz, Mangrovenlaub, Ranken.'),
    'weeping_willow.lore': ('Orpheus carried a willow branch into the underworld.',
                            'Orpheus trug einen Weidenzweig in die Unterwelt.'),
    'weeping_willow.plant': ('Plant on dirt or grass. Grows with light or bone meal. Four in a square grow a big '
                             'willow.',
                             'Auf Erde oder Gras. Wächst mit Licht oder Knochenmehl. Vier im Quadrat ergeben eine '
                             'große Weide.'),

    'aries_oak.name': ('Aries Oak', 'Widdereiche'),
    'aries_oak.about': ('A giant, 53–60 high and as wide, with hollow crowns to build in. Its leaves never decay.',
                        'Ein Riese, 53–60 hoch und ebenso breit, mit hohlen Kronen zum Bauen. Sein Laub verfällt '
                        'nie.'),
    'aries_oak.lore': ('Like the oak of Dodona, in whose leaves Zeus was heard.',
                       'Wie die Eiche von Dodona, in deren Laub man Zeus hörte.'),
    'aries_oak.dedication': ('Dedicated to SassyAries00.', 'SassyAries00 gewidmet.'),  # one line: the page is full
    'aries_oak.plant': ('Only grows from 16 saplings in a 4×4 square. Fewer never grow and take no bone meal.',
                        'Wächst nur aus 16 Setzlingen im 4×4-Quadrat. Weniger wachsen nie und nehmen kein '
                        'Knochenmehl.'),

    'big': ('Large Trees', 'Große Bäume'),
    'big.square': ('Four saplings in a square.', 'Vier Setzlinge im Quadrat.'),
    'large_date_palm.name': ('Large Date Palm', 'Große Dattelpalme'),
    'large_date_palm.about': ('Two to five tall trunks slanting apart, 19–27 high.',
                              'Zwei bis fünf hohe Stämme, die auseinanderstreben, 19–27 hoch.'),
    'large_weeping_willow.name': ('Big Weeping Willow', 'Große Trauerweide'),
    'large_weeping_willow.about': ('A 2×2 trunk and more strands along the rim, 15–25 high.',
                                   'Stamm 2×2 und mehr Stränge am Rand, 15–25 hoch.'),

    'fruit': ('Fruit', 'Früchte'),
    'fruit.text': ('Fruit ripens in three stages, like cocoa; bone meal helps. Right click ripe fruit: 2–3 fruit, '
                   'the twig stays and starts over. Breaking unripe fruit gives one. Plant fruit on a leaf block to '
                   'hang a new twig; dates go on the side of jungle wood. Breaking a tree\'s leaves sometimes drops '
                   'fruit.',
                   'Früchte reifen in drei Stufen wie Kakao; Knochenmehl hilft. Rechtsklick auf reife Früchte: 2–3 '
                   'Früchte, der Zweig bleibt und beginnt von vorn. Unreif abgebaut gibt es eine. Frucht auf einen '
                   'Laubblock setzen: ein neuer Zweig; Datteln an die Seite von Tropenholz. Laub eines Baums lässt '
                   'manchmal Früchte fallen.'),
    'fruit.row': ('%1$s: %2$s hunger · %3$s', '%1$s: %2$s Hunger · %3$s'),

    'pits': ('Olive Pits', 'Olivenkerne'),
    'pits.text': ('Eating an olive leaves a pit. Throw it like a snowball, by hand or from a dispenser: 1.5 hearts. '
                  'Two pits on top of each other make a sharpened pit: 3.5 hearts. Both break on impact.',
                  'Vom Essen einer Olive bleibt ein Kern. Er fliegt wie ein Schneeball, aus der Hand oder dem '
                  'Werfer: 1,5 Herzen. Zwei Kerne übereinander ergeben einen geschärften Kern: 3,5 Herzen. Beide '
                  'zerbrechen beim Aufprall.'),
}


# ================================================================ pictures (python tools/make_book.py)

def fit(im, w, h, resample=None):
    from PIL import Image
    f = min(w / im.width, h / im.height)
    return im.resize((max(1, round(im.width * f)), max(1, round(im.height * f))), resample or Image.LANCZOS)


def tree_picture(voxel, voxels, size=PIC):
    """Iso render with its grass plate on nothing: the screen draws the page and the frame behind it."""
    from PIL import Image
    page = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    iso = voxel.render(voxels, margin=0)
    pad = size // 28
    im = fit(iso.crop(iso.getbbox()), size - 2 * pad, size - 2 * pad)
    page.alpha_composite(im, ((size - im.width) // 2, (size - im.height) // 2))
    return page


# ---------------------------------------------------------------- item texture

JAR = os.path.expanduser('~/.gradle/caches/fabric-loom/1.21.1/minecraft-client.jar')
# vanilla book colours -> the mod icon's: blue cover, dark blue edges and lines, cream pages
BOOK_COLOURS = {(0x31, 0x21, 0x04): (16, 34, 72), (0x16, 0x10, 0x05): (8, 18, 40), (0x52, 0x2e, 0x10): (30, 66, 128),
                (0x54, 0x3e, 0x13): (36, 78, 146), (0x65, 0x4b, 0x17): BLUE, (0x44, 0x25, 0x0a): DBLUE,
                (0xd6, 0xd6, 0xd6): CREAM, (0xb7, 0xb7, 0xb7): (218, 204, 170), (0x99, 0x99, 0x99): (180, 162, 126),
                (0x5b, 0x5b, 0x5b): (120, 104, 76)}
COVER_FACE = [(0, 5.5), (9.5, 1), (15.5, 6.5), (6, 11)]  # the vanilla book's cover, seen at a slant


def vanilla_book():
    from PIL import Image
    return Image.open(io.BytesIO(zipfile.ZipFile(JAR).read('assets/minecraft/textures/item/book.png'))).convert('RGBA')


def label(scale, cx=7.7, cy=5.6):
    """The pixels of the cover face shrunk to `scale` round its middle: a label slanted like the cover."""
    poly = [(cx + scale * (x - cx), cy + scale * (y - cy)) for x, y in COVER_FACE]

    def inside(x, y):
        hit = False
        for (x1, y1), (x2, y2) in zip(poly, poly[-1:] + poly[:-1]):
            if (y1 > y) != (y2 > y) and x < x1 + (y - y1) * (x2 - x1) / (y2 - y1):
                hit = not hit
        return hit
    return [(x, y) for y in range(16) for x in range(16) if inside(x + .5, y + .5)]


# the green tree printed on the label, lying in the slanted cover (rows from y 4, h light, g mid, d dark green, t trunk).
# The book's foot is its lower left side, so the tree runs from the lower left to the upper right along the long
# edge (5.7 pixels right, 2.7 up): foot on the label's lower left edge, crown in the upper right half.
LABEL_TREE = ['........hh', '.......hggg', '......ttdd', '.....t']
TREE_COLOURS = {'h': (122, 160, 84), 'g': (84, 128, 58), 'd': (58, 98, 44), 't': (86, 56, 34)}


def item_texture(variant):
    """The vanilla book in the cover's colours. 1: only that; 2: a cream label on the cover with the green tree,
    like the cover's field and sapling, drawn flat in the slanted cover (B2 picked 2026-10-05, the tree then stood
    upright)."""
    img = vanilla_book()
    img.putdata([BOOK_COLOURS[p[:3]] + (255,) if p[3] else p for p in img.get_flattened_data()])
    if variant == 2:
        field = label(0.6)
        for p in field:
            img.putpixel(p, CREAM + (255,))
        for dy, row in enumerate(LABEL_TREE):
            for x, c in enumerate(row):
                if c in TREE_COLOURS:
                    assert (x, 4 + dy) in field, f'tree pixel {x},{4 + dy} is off the label'
                    img.putpixel((x, 4 + dy), TREE_COLOURS[c] + (255,))
    return img


# ---------------------------------------------------------------- preview sheets

def preview_item():
    from PIL import Image, ImageDraw, ImageFont
    title = ImageFont.truetype('C:/Windows/Fonts/segoeuib.ttf', 24)
    text = ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf', 15)
    items = [('B1 · nur umgefärbt', item_texture(1)), ('B2 · Baum von links unten nach rechts oben', item_texture(2)),
             ('Vanilla-Buch', vanilla_book())]
    sheet = Image.new('RGBA', (24 + 300 * len(items), 330), (245, 241, 232, 255))
    d = ImageDraw.Draw(sheet)
    d.text((24, 12), 'Item-Textur: das Vanilla-Buch in den Farben des Covers', font=title, fill=(29, 53, 87, 255))
    for i, (name, im) in enumerate(items):
        x = 24 + 300 * i
        sheet.alpha_composite(im.resize((128, 128), Image.NEAREST), (x, 60))
        slot = Image.new('RGBA', (36, 36), (139, 139, 139, 255))  # an inventory slot at GUI size 2
        slot.alpha_composite(im.resize((32, 32), Image.NEAREST), (2, 2))
        sheet.alpha_composite(slot, (x + 150, 60))
        sheet.alpha_composite(im, (x + 200, 70))
        sheet.alpha_composite(im.resize((48, 48), Image.NEAREST), (x + 150, 110))
        d.text((x, 200), name, font=text, fill=(29, 53, 87, 255))
    d.text((24, 290), 'je 8×, Slot bei GUI-Größe 2, 16 px, 3×', font=text, fill=(120, 128, 140, 255))
    out = os.path.join(ART, 'preview_item.png')
    sheet.save(out)
    return out


def preview_trees(pictures):
    from PIL import Image, ImageDraw, ImageFont
    from render_selftest import TITLES
    title = ImageFont.truetype('C:/Windows/Fonts/segoeuib.ttf', 24)
    text = ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf', 15)
    cw, rh = 250, 262
    sheet = Image.new('RGBA', (200 + 4 * cw, 80 + rh * len(PICTURES)), (245, 241, 232, 255))
    d = ImageDraw.Draw(sheet)
    d.text((24, 12), 'Baumbilder: die ersten vier Wüchse des letzten Selbsttests', font=title, fill=(29, 53, 87, 255))
    d.text((24, 46), f'je {PIC} px (so groß bei GUI-Größe 2); rot umrandet: der vorgeschlagene', font=text,
           fill=(120, 128, 140, 255))
    for r, name in enumerate(PICTURES):
        y = 80 + r * rh
        d.text((24, y + 100), TITLES[name].replace(' ', '\n'), font=text, fill=(29, 53, 87, 255))
        for n, pic in enumerate(pictures[name]):
            x = 200 + n * cw
            if n == PICK[name]:
                d.rectangle([x - 5, y - 5, x + PIC + 4, y + PIC + 4], outline=(200, 40, 40, 255), width=3)
            sheet.alpha_composite(pic, (x, y))
            d.text((x, y + PIC + 6), f'Wuchs {n}', font=text, fill=(120, 128, 140, 255))
    out = os.path.join(ART, 'preview_tree_pictures_all.png')
    sheet.save(out)
    return out


def preview_pictures():
    """The tree pictures as the book shows them: at 96 GUI pixels (here 2x) on the page in its thin frame."""
    from PIL import Image, ImageDraw
    sheet = Image.new('RGBA', (16 + 5 * (PIC + 16), 16 + 2 * (PIC + 16)), (63, 116, 184, 255))
    d = ImageDraw.Draw(sheet)
    for i, name in enumerate(PICTURES):
        x, y = 16 + i % 5 * (PIC + 16), 16 + i // 5 * (PIC + 16)
        d.rectangle([x, y, x + PIC - 1, y + PIC - 1], fill=PAGE, outline=FRAME, width=2)
        sheet.alpha_composite(Image.open(os.path.join(GUI, name + '.png')).convert('RGBA'), (x, y))
    out = os.path.join(ART, 'preview_pictures.png')
    sheet.save(out)
    return out


def main():
    os.makedirs(GUI, exist_ok=True)
    os.makedirs(ART, exist_ok=True)
    item_texture(BOOK_ITEM).save(os.path.join(ASSETS, 'textures', 'item', 'guide_book.png'))
    print(preview_item())
    os.chdir(os.path.join(ROOT, 'concept'))  # the renderer modules read their files relative to concept/
    sys.path.insert(0, '.')
    import voxel
    from render_selftest import load
    if 'choose' in sys.argv:
        print(preview_trees({name: [tree_picture(voxel, load(f'{name}_{n}')) for n in range(4)
                                    if os.path.exists(os.path.join('..', 'run', 'greektrees-selftest', f'{name}_{n}.json'))]
                             for name in PICTURES}))
        return
    for name in PICTURES:
        tree_picture(voxel, load(f'{name}_{PICK[name]}', os.path.join(ART, 'trees'))).save(os.path.join(GUI, name + '.png'))
    print(preview_pictures())


if __name__ == '__main__':
    main()
