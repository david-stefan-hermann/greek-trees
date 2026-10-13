"""Renders the trees the mod grew on the dev server (run-1.21.1/greektrees-selftest/<species>_<n>.json).

python render_selftest.py                -> selftest_trees.png (first growth of every species)
python render_selftest.py olive date_palm -> selftest_olive.png, selftest_date_palm.png (six growths each)
python render_selftest.py drafts p1 w1    -> drafts_p1.png, drafts_w1.png (run-1.21.1/greektrees-drafts/, the rounds
                                             of PLAN-PALME-WEIDE.md written by `./gradlew runServer -Pdrafts=p1,w1`)
python render_selftest.py trunks          -> willow_trunks.png (the willow trunks rebuilt by hand and rolled ones)
"""
import json
import os
import sys
import textwrap

from PIL import Image, ImageDraw

import voxel
from make_round5 import scaled
from make_round6 import drawable
from make_sheet import F_LABEL, F_TEXT, F_TITLE, GREY, INK, PAPER, sky

DIR = os.path.join('..', 'run-1.21.1', 'greektrees-selftest')
TITLES = {'cypress': 'Zypresse', 'olive': 'Olivenbaum', 'fig': 'Feigenbaum', 'strawberry_tree': 'Erdbeerbaum',
          'date_palm': 'Kretische Dattelpalme', 'large_date_palm': 'Große Dattelpalme', 'mulberry': 'Maulbeerbaum',
          'weeping_willow': 'Trauerweide', 'large_weeping_willow': 'Große Trauerweide', 'aries_oak': 'Widdereiche',
          'aries_oak_weeping': 'Widdereiche mit Blattsträngen'}
GIANTS = {'aries_oak': 4, 'aries_oak_weeping': 3}  # drawn smaller, all growths (the self-test grows four)


DRAFT_DIR = os.path.join('..', 'run-1.21.1', 'greektrees-drafts')
# The rounds of PLAN-PALME-WEIDE.md (shapes from greektrees.tree.Drafts): title, subtitle, (letter, name, what).
DRAFTS = {
    'p1': ('Dattelpalme · Runde P1: die Krone',
           'Je Zeile eine Variante, drei Wüchse mit denselben Würfen (gleicher Stamm, 10 hoch); '
           'links Iso-Ansicht, rechts Seitenansicht.', [
               ('0', 'jetzt', 'Geschlossene Kappe 9×9: vier Wedel, dazwischen Füllblätter.'),
               ('A', 'Stern', 'Flacher Stern aus acht feinen Wedeln, lange gerade und kürzere diagonale, '
                              'nichts dazwischen. Dauerhaftes Laub.'),
               ('B', 'Federball', 'Hohe, runde Krone aus feinen Wedeln: oben steil aufwärts, in der Mitte '
                                  'waagrecht, unten hängend. Dauerhaftes Laub.'),
               ('C', 'Windschief', 'Sieben bis neun feine Wedel in beliebige Richtungen. Zur Neigung des '
                                   'Stamms hin hängen sie, auf der anderen Seite steigen sie. Dauerhaftes Laub.'),
               ('D', 'Groß, kräftig', 'Zwei Kränze langer Wedel (5–7 Blöcke) mit hängenden Spitzen; die Wedel '
                                      'sind lückenlos gebaut. Dauerhaftes Laub.'),
               ('E', 'Groß, fein', 'Wie D, aber die Wedel so fein wie bei A–C (Blätter berühren sich nur '
                                   'über Kanten). Dauerhaftes Laub.')]),
    'w1': ('Trauerweide · Runde W1: der Umriss der Krone',
           'Je Zeile eine Variante, drei Wüchse mit denselben Würfen (gleicher Stamm, mittlere Größe); '
           'Vorhang überall nach der jetzigen Regel. Ranken sind als Würfel gezeichnet.', [
               ('0', 'jetzt', 'Halbkugel, die Äste in Laub gehüllt.'),
               ('A', 'Kissen', 'Keine Kuppel: ein Laubkissen auf dem Leittrieb und eines auf jedem Astende, '
                               'jedes auf eigener Höhe. Strähnen fallen vom Rand jedes Kissens.'),
               ('B', 'Fontäne', 'Äste steigen steil aus dem Stamm, laufen sichtbar über einen Bogen und '
                                'kommen weit außen herunter. Laub nur außen am Bogen, über dem Stamm offen.'),
               ('C', 'Glocke', 'Kleine Kuppel oben, darunter zwei Astkränze; jeder tiefere reicht weiter '
                               'hinaus. Unten am breitesten.'),
               ('D', 'Schirm', 'Höherer Stamm mit sichtbarer Gabelung, darauf ein flaches, breites Dach. '
                               'Der Vorhang macht den größten Teil der Höhe aus.')]),
    'p2': ('Dattelpalme · Runde P2: Stern und Federball',
           'Je Zeile eine Variante, drei Wüchse mit denselben Würfen (gleicher Stamm: sanfter Bogen, 9–11 hoch); '
           'links Iso-Ansicht, rechts Seitenansicht. Alle mit dauerhaftem Laub.', [
               ('A1', 'Stern', 'Wie in Runde 1: acht Wedel, lange entlang der Achsen, kürzere diagonal.'),
               ('A2', 'Stern, hängend', 'Längere Wedel (6–7), alle Spitzen hängen 2–4 Blöcke herab.'),
               ('A3', 'Stern, frei', 'Sieben bis neun Wedel, der Stern beliebig gedreht, jede Länge gewürfelt. '
                                     'Keine zwei Kronen gleich.'),
               ('B1', 'Federball', 'Wie in Runde 1: je vier Wedel steil, schräg, waagrecht und hängend.'),
               ('B2', 'Federball, groß', 'Dieselbe Krone, alle Wedel um ein Drittel länger.'),
               ('B3', 'Federball, frei', 'Drei bis fünf Wedel je Kranz, jeder etwas verdreht und mit '
                                         'gewürfelter Länge. Keine zwei Kronen gleich.'),
               ('AB', 'Stern mit Schopf', 'Der Stern aus A1, darüber vier steile Wedel wie beim Federball.')]),
    'p3': ('Dattelpalme · Runde P3: der Stamm',
           'Je Zeile eine Variante, drei Wüchse mit denselben Würfen (gleiche Höhe und Richtung, Krone: Stern); '
           'links Iso-Ansicht, rechts Seitenansicht.', [
               ('0', 'jetzt', 'Bogen mit drei bis vier Versätzen, nach oben immer dichter: unten gerade, '
                              'oben Treppe.'),
               ('A', 'Sanfter Bogen', 'Unten gerade, zwei Versätze im oberen Teil mit Abstand, drei gerade '
                                      'Blöcke unter der Krone.'),
               ('B', 'Bogen am Fuß', 'Der Stamm verlässt den Boden schräg (zwei bis drei Versätze unten) und '
                                     'steht darüber gerade.'),
               ('C', 'S-Schwung', 'Unten zwei Versätze hinaus, oben einer zurück.'),
               ('D', 'Fast gerade', 'Ein einziger Versatz im mittleren Drittel.'),
               ('E', 'Schräg', 'Gleichmäßig geneigt: alle drei Blöcke ein Versatz.')]),
    'w3': ('Trauerweide · Runde W3: der Stamm',
           'Je Zeile eine Variante, drei Wüchse mit denselben Würfen (gleiche Krone, Höhe und Richtung); die Krone '
           'ist die jetzige. Je Wuchs: klein der ganze Baum, groß nur das Holz, rechts das Holz von der Seite.', [
               ('0', 'jetzt', 'Kurve mit Gegenschwung, an jedem Versatz ein Knie; zwei bis drei Wurzelblöcke.'),
               ('A', 'Ein Bogen', 'Unten gerade, dann ein bis zwei Versätze in eine Richtung, kein '
                                  'Gegenschwung.'),
               ('B', 'Kräftiger Fuß', 'Wie A, aber der Fuß ist ringsum verdickt, an zwei Seiten zwei Blöcke '
                                      'hoch, dazu zwei bis drei Wurzeln.'),
               ('C', 'Gabel', 'Wie A, dazu ein bis zwei Äste, die auf halber Höhe abzweigen und in die '
                              'Kuppel steigen.'),
               ('D', 'Geneigt', 'Der Stamm lehnt vom Boden an in eine Richtung, alle zwei Blöcke ein Versatz.'),
               ('E', 'Dicker Stamm', 'Wie B, dazu ist der Stamm bis zum ersten Versatz zwei Blöcke stark '
                                     '(auf der Seite, von der er sich wegneigt).')]),
    'p4': ('Dattelpalme · Runde P4: ein, zwei und drei Stämme',
           'Gewählte Krone (Stern mit Schopf) und gewählter Stamm (gleichmäßig schräg, nur in eine Richtung). '
           'Spalten: ein Stamm, zwei Stämme, drei Stämme; links Iso-Ansicht, rechts Seitenansicht.', [
               ('0', 'jetzt', 'Die Dattelpalme aus 0.15.0 zum Vergleich.'),
               ('A', 'Volle Kronen', 'Jeder Stamm trägt die ganze Krone. Füße wie jetzt direkt aneinander, '
                                     'jeder Stamm neigt sich von den anderen weg.'),
               ('B', 'Gestufte Kronen', 'Wie A, aber die niedrigeren Stämme tragen kleinere Kronen '
                                        '(Wedel 4 und 3 statt 5 Blöcke).'),
               ('C', 'Gestuft, weiter auseinander', 'Wie B; die niedrigeren Stämme neigen sich stärker (alle '
                                                    'zwei Blöcke ein Versatz), der dritte bleibt niedriger.'),
               ('D', 'Gestuft, Füße getrennt', 'Wie B, aber die Stämme wurzeln zwei Blöcke auseinander.')]),
    'w4': ('Trauerweide · Runde W4: Stamm und Geäst',
           'Kuppel und Vorhang entstehen nach den jetzigen Regeln mit denselben Würfen; es ändert sich das Holz '
           '(wo Äste anders laufen, sitzt auch ihr Laub etwas anders). Je Wuchs: klein der ganze Baum, groß nur das Holz, rechts das Holz von der Seite.', [
               ('0', 'jetzt', 'Dünner Stamm mit Kurve und Knien, darüber ein gerader Leittrieb, von dem '
                              'alle Äste wie Schirmrippen ausgehen.'),
               ('A', 'Kandelaber', 'Stamm neigt sich nur in eine Richtung. Kein Leittrieb: unter der Kuppel '
                                   'teilt er sich in drei bis vier kräftige Äste.'),
               ('B', 'Kandelaber, kräftig', 'Wie A, der Stamm ist im unteren Drittel ringsum verdickt, dazu '
                                            'zwei bis drei Wurzeln.'),
               ('C', 'Zwiesel', 'Gerader Fuß, der sich schon nach zwei bis drei Blöcken in zwei oder drei '
                                'Stämme gabelt; sie steigen schräg bis unter die Kuppel.'),
               ('D', 'Zwiesel, kräftig', 'Wie C mit verdicktem Fuß und Wurzeln.')]),
    'p5': ('Dattelpalme · Runde P5: die Palme aus einem Setzling, Stand der Auswahl',
           'Krone Stern mit Schopf, auf niedrigeren Stämmen kleiner; Stamm gleichmäßig schräg in eine Richtung; '
           'Füße nur diagonal aneinander. Spalten: ein, zwei, drei Stämme.', [
               ('V', 'Dritter Stamm daneben', 'Der zweite Stamm steht an einer Ecke des ersten, der dritte an '
                                              'der Ecke daneben: von oben ein V.'),
               ('L', 'Dritter Stamm gegenüber', 'Der dritte Stamm steht an der gegenüberliegenden Ecke: von '
                                                'oben eine diagonale Linie. (Mit ein und zwei Stämmen gleich '
                                                'wie V, nur anders gewürfelt.)')]),
    'p6': ('Dattelpalme · Runde P6: die große Palme aus vier Setzlingen',
           'Mit der gewählten Krone und dem gewählten Stamm; Füße wie bisher (Ecke des Hauptstamms oder zwei '
           'Blöcke entfernt, nie nebeneinander). Spalten: drei, vier, fünf Stämme.', [
               ('0', 'jetzt', 'Die große Dattelpalme aus 0.15.0 zum Vergleich.'),
               ('A', 'Volle Kronen', 'Jeder Stamm trägt die ganze Krone; jeder neigt sich nach außen.'),
               ('B', 'Gestufte Kronen', 'Der höchste Stamm trägt die ganze Krone, die nächsten beiden die '
                                        'mittlere, weitere die kleine.'),
               ('C', 'Gestuft und gestaffelt', 'Wie B; der Hauptstamm ist höher (15–17), die anderen halb bis '
                                               'vier Fünftel so hoch und stärker geneigt.')]),
    'w5': ('Trauerweide · Runde W5: wie der Stamm sich krümmt',
           'Krone und Geäst sind die jetzigen und je Wuchs in allen Zeilen gleich. Der Stamm bewegt sich nur '
           'entlang einer Achse. Je Wuchs: klein der ganze Baum, groß der Stamm allein bis zum Kronenansatz, '
           'rechts der Stamm von der Seite.', [
               ('0', 'jetzt', 'Kurve in zwei Richtungen; an jedem Versatz ein Knie (ein Block neben dem '
                              'nächsten).'),
               ('A', 'Geneigt, Kante an Kante', 'Ein bis zwei Versätze zur selben Seite. Am Versatz berühren '
                                                'sich die Blöcke nur über die Kante, wie bei der Palme.'),
               ('B', 'Geneigt, langes Knie', 'Wie A, aber am Versatz laufen alter und neuer Stamm zwei Blöcke '
                                             'hoch nebeneinander.'),
               ('C', 'Geneigt, doppelt', 'Wie A, aber der Stamm ist durchgehend zwei Blöcke stark (in '
                                         'Neigungsrichtung); jeder Versatz überlappt um einen Block.'),
               ('D', 'Bogen, Knie', 'Der Stamm geht einen Block hinaus (bei hohen zwei) und kommt wieder '
                                    'zurück: die Krone steht über dem Fuß. Knie wie jetzt.'),
               ('E', 'Bogen, langes Knie', 'Der Bogen aus D mit zwei Blöcke hohen Übergängen.'),
               ('F', 'Bogen, doppelt', 'Der Bogen aus D, der Stamm durchgehend zwei Blöcke stark.')]),
    'w6': ('Trauerweide · Runde W6: der Bogen, auch diagonal',
           'Gewählt: Bogen mit Knie (D). Krone und Geäst wie jetzt. Je Wuchs: klein der ganze Baum, groß der '
           'Stamm allein bis zum Kronenansatz, rechts der Stamm von zwei Seiten (Süden, Osten).', [
               ('0', 'jetzt', 'Kurve in zwei Richtungen zum Vergleich.'),
               ('D', 'Bogen entlang einer Achse', 'Wie gewählt: einen Block hinaus (bei hohen zwei) und '
                                                  'wieder zurück, Knie an jedem Versatz.'),
               ('G', 'Diagonal in Einzelschritten', 'Der Bogen läuft über Eck: ein Versatz entlang x, einer '
                                                    'entlang z, dann auf demselben Weg zurück. Jeder Versatz '
                                                    'mit Knie. Stämme unter fünf Blöcken bleiben beim Bogen '
                                                    'aus D.'),
               ('H', 'Diagonal in einem Schritt', 'Der Stamm springt über Eck (x und z zugleich) und zurück. '
                                                  'Am Versatz ein Winkelknie: der alte Stamm läuft einen '
                                                  'Block weiter, ein Block verbindet über Eck.')]),
    'w7': ('Trauerweide · Runde W7: die große Weide mit Bogen',
           'Vier Setzlinge, Stamm 2×2; Krone und Geäst wie jetzt. Die 2×2-Schichten überlappen am Versatz, ein '
           'Knie braucht es nicht. Je Wuchs: klein der ganze Baum, groß der Stamm allein, rechts von zwei Seiten.', [
               ('0', 'jetzt', 'Kurve in zwei Richtungen zum Vergleich.'),
               ('A', 'Bogen entlang einer Achse', 'Einen Block hinaus (bei hohen zwei) und wieder zurück.'),
               ('B', 'Bogen diagonal', 'Derselbe Bogen über Eck: jede Schicht versetzt um einen Block in x '
                                       'und z zugleich.')]),
}


# rounds about the trunk alone, drawn large without the crown's limbs: the most logs a layer of the trunk holds
TRUNK = {'w5': 2, 'w6': 3, 'w7': 4}
BARE = {'w3', 'w4'}  # rounds about the trunk of a tree whose leaves hide it


def load(name, folder=DIR):
    v = {}
    for x, y, z, block, axis in json.load(open(os.path.join(folder, name + '.json'))):
        v[(x, y, z)] = (block, axis)
    v = drawable(v)  # glow lichen: the JSON gives the side it lies on
    xs = [p[0] for p in v]
    zs = [p[2] for p in v]
    for x in range(min(xs) - 1, max(xs) + 2):
        for z in range(min(zs) - 1, max(zs) + 2):
            v.setdefault((x, -1, z), ('grass_block', 'y'))
    return v


def sheet(entries, title, subtitle, out, scale=1.0, side=10):
    """entries: list of (label, voxels); iso views on top, side views below, same scale."""
    imgs = [(label, scaled(voxel.render(v, margin=10), scale), voxel.render_side(v, s=side, margin=8))
            for label, v in entries]
    W = sum(max(i.width, s.width) for _, i, s in imgs) + 40 * len(imgs) + 20
    H1 = max(i.height for _, i, _ in imgs)
    H2 = max(s.height for _, _, s in imgs)
    img = Image.new('RGBA', (W, 100 + H1 + H2 + 90), PAPER)
    d = ImageDraw.Draw(img)
    d.text((24, 14), title, font=F_TITLE, fill=INK)
    d.text((26, 60), subtitle, font=F_TEXT, fill=GREY)
    panel = sky(W - 40, H1 + H2 + 50)
    x = 20
    for label, iso, side in imgs:
        w = max(iso.width, side.width)
        panel.alpha_composite(iso, (x + (w - iso.width) // 2, H1 - iso.height))
        panel.alpha_composite(side, (x + (w - side.width) // 2, H1 + H2 + 10 - side.height))
        pd = ImageDraw.Draw(panel)
        tw = pd.textlength(label, font=F_LABEL)
        pd.text((x + (w - tw) / 2, H1 + H2 + 18), label, font=F_LABEL, fill=INK)
        x += w + 40
    img.alpha_composite(panel, (20, 100))
    img.save(out)
    print(out, img.size)


def joined(logs, top):
    """The logs joined to the foot (y 0) over faces, edges or corners, up to layer top: limb ends that hang down
    to the trunk's height stay out."""
    todo = [p for p in logs if p[1] == 0]
    seen = set(todo)
    while todo:
        x, y, z = todo.pop()
        for q in ((x + i, y + j, z + k) for i in (-1, 0, 1) for j in (-1, 0, 1) for k in (-1, 0, 1)):
            if q in logs and q not in seen and q[1] <= top:
                seen.add(q)
                todo.append(q)
    return {p: logs[p] for p in seen}


def trunk_views(logs):
    """A trunk alone on a small piece of grass: iso view, then side views from the east and from the south."""
    xs, zs = [p[0] for p in logs], [p[2] for p in logs]
    v = dict(logs)
    for x in range(min(xs) - 2, max(xs) + 3):
        for z in range(min(zs) - 2, max(zs) + 3):
            v[(x, -1, z)] = ('grass_block', 'y')
    steve = (max(xs) + 2, max(zs) + 2)
    turned = {(z, y, -x): b for (x, y, z), b in v.items()}  # the same seen from the east
    return [voxel.render(v, steve=steve, margin=10),
            voxel.render_side(turned, s=16, steve=(steve[1], -steve[0]), margin=8),
            voxel.render_side(v, s=16, steve=steve, margin=8)]


# the willow trunks the user rebuilt by hand (concept/reference/) and their heights, as measured in PLAN-0.17.md E.1
REFERENCES = {'small_1': 5, 'small_2': 9, 'small_3': 9, 'big_1': 11, 'big_2': 10, 'big_3': 9}


def trunk_sheet():
    """willow_trunks.png: the six trunks rebuilt by hand, below them nine rolled thin and nine rolled big trunks
    (run-1.21.1/greektrees-selftest/willow_trunk_roll_*.json, written by the self-test)."""
    rows = [('Von Hand umgebaut (Referenz)',
             [(n.replace('_', ' '), joined({p: b for p, b in load('willow_trunk_' + n, 'reference').items()
                                            if voxel.is_log(b[0])}, th)) for n, th in REFERENCES.items()]),
            ('Gewürfelt: kleine Weide', [(f'Wurf {n + 1}', load(f'willow_trunk_roll_small_{n}')) for n in range(9)]),
            ('Gewürfelt: große Weide', [(f'Wurf {n + 1}', load(f'willow_trunk_roll_big_{n}')) for n in range(9)])]
    rows = [(title, [(label, trunk_views({p: b for p, b in v.items() if p[1] >= 0})) for label, v in trunks])
            for title, trunks in rows]
    widths = [sum(sum(i.width + 6 for i in views) + 30 for _, views in trunks) for _, trunks in rows]
    heights = [max(i.height for _, views in trunks for i in views) + 80 for _, trunks in rows]
    W = max(widths) + 60
    img = Image.new('RGBA', (W, 110 + sum(heights) + 20), PAPER)
    d = ImageDraw.Draw(img)
    d.text((24, 14), 'Trauerweide · der Stamm (0.17.0)', font=F_TITLE, fill=INK)
    d.text((26, 60), 'Jeder Stamm allein bis zum Kronenansatz: Iso-Ansicht, von Osten, von Süden. Oben die sechs von '
                     'Hand umgebauten, darunter die ersten neun Würfe des Selbsttests.', font=F_TEXT, fill=GREY)
    y = 100
    for (title, trunks), h in zip(rows, heights):
        panel = sky(W - 40, h - 10)
        pd = ImageDraw.Draw(panel)
        pd.text((16, 10), title, font=F_LABEL, fill=INK)
        x = 16
        for label, views in trunks:
            pd.text((x, h - 44), label, font=F_TEXT, fill=INK)
            for im in views:
                panel.alpha_composite(im, (x, h - 50 - im.height))
                x += im.width + 6
            x += 30
        img.alpha_composite(panel, (20, y))
        y += h
    img.save('willow_trunks.png')
    print('willow_trunks.png', img.size)


def draft_sheet(key, scale=0.7, side=8):
    """drafts_<key>.png: one row per variant (letter, name, what changes), three growths each with a player."""
    title, sub, variants = DRAFTS[key]
    rows = []
    for letter, name, what in variants:
        ims = []
        for n in range(3):
            v = load(f'draft_{key}_{letter}_{n}', DRAFT_DIR)
            steve = (max(p[0] for p in v), max(p[2] for p in v))  # on the front corner of the grass
            views = [scaled(voxel.render(v, steve=steve, margin=10), 0.4 if key in BARE or key in TRUNK else scale)]
            if key in BARE:  # the trunk hides behind the leaves: the whole tree small, the wood alone large
                v = {p: b for p, b in v.items() if voxel.is_log(b[0]) or b[0] == 'grass_block'}
                views.append(scaled(voxel.render(v, steve=steve, margin=10), scale))
            if key in TRUNK:  # only the trunk, up to the first layer of the crown, on a small piece of grass
                per_layer = {}
                for (x, y, z), (b, _) in v.items():
                    if voxel.is_log(b):
                        per_layer[y] = per_layer.get(y, 0) + 1
                cut = next((y for y in sorted(per_layer) if y >= 1 and per_layer[y] > TRUNK[key]), max(per_layer))
                views = views[:1] + trunk_views(joined({p: b for p, b in v.items() if voxel.is_log(b[0])}, cut - 1))
            else:
                views.append(voxel.render_side(v, s=side, steve=steve, margin=8))
            ims.append(views)
        rows.append((letter, name, what, ims))
    label_w = 330
    col_w = [max(sum(i.width + 10 for i in r[3][n]) for r in rows) + 40 for n in range(3)]
    row_h = [max(i.height for views in r[3] for i in views) + 30 for r in rows]
    W = label_w + sum(col_w) + 60
    img = Image.new('RGBA', (W, 110 + sum(row_h) + 20), PAPER)
    d = ImageDraw.Draw(img)
    d.text((24, 14), title, font=F_TITLE, fill=INK)
    d.text((26, 60), sub, font=F_TEXT, fill=GREY)
    y = 100
    for (letter, name, what, ims), h in zip(rows, row_h):
        panel = sky(W - 40, h - 10)
        pd = ImageDraw.Draw(panel)
        pd.text((16, 12), letter, font=F_TITLE, fill=INK)
        pd.text((30 + pd.textlength(letter, font=F_TITLE), 22), name, font=F_LABEL, fill=INK)
        for k, line in enumerate(textwrap.wrap(what, 36)):
            pd.text((16, 66 + k * 24), line, font=F_TEXT, fill=INK)
        x = label_w
        for views, w in zip(ims, col_w):
            vx = x
            for im in views:
                panel.alpha_composite(im, (vx, h - 20 - im.height))
                vx += im.width + 10
            x += w
        img.alpha_composite(panel, (20, y))
        y += h
    out = f'drafts_{key}.png'
    img.save(out)
    print(out, img.size)


def main():
    keys = sys.argv[1:]
    if keys == ['trunks']:
        trunk_sheet()
        return
    if keys and keys[0] == 'drafts':
        for key in keys[1:]:
            draft_sheet(key)
        return
    if not keys:
        sheet([(TITLES[k], load(k + '_0')) for k in TITLES],
              'Gewachsen im Dev-Server (Mod-Code, je der erste von 12 Wüchsen)',
              'Blöcke 1:1 aus der Welt ausgelesen und gerendert; oben Iso-Ansicht, unten Seitenansicht.',
              'selftest_trees.png')
        return
    for k in keys:
        if k in GIANTS:
            sheet([(f'Wuchs {n + 1}', load(f'{k}_{n}')) for n in range(GIANTS[k])],
                  f'{TITLES[k]} · {GIANTS[k]} Wüchse aus dem Dev-Server',
                  'Mod-Code, Blöcke 1:1 aus der Welt ausgelesen; oben Iso-Ansicht, unten Seitenansicht; '
                  'Stufen als braune Kästen', f'selftest_{k}.png', scale=0.45, side=5)
            continue
        picks = list(range(6))
        trunks_of = {}
        if k in ('date_palm', 'large_date_palm'):  # show every trunk count: the first two growths of each
            by_trunks = {}
            for n in range(12):
                if not os.path.exists(os.path.join(DIR, f'{k}_{n}.json')):
                    break  # the big trees of four saplings grow six times
                cells = load(f'{k}_{n}')
                trunks = sum(1 for (x, y, z), (b, _) in cells.items() if y == 0 and b == 'jungle_wood')
                by_trunks.setdefault(trunks, []).append(n)
                trunks_of[n] = trunks
            picks = [n for t in sorted(by_trunks) for n in by_trunks[t][:2]][:8]
        sheet([(f'Wuchs {n + 1}' + (f' · {trunks_of[n]} Stämme' if n in trunks_of else ''), load(f'{k}_{n}'))
               for n in picks],
              f'{TITLES[k]} · {len(picks)} Wüchse aus dem Dev-Server',
              'Mod-Code, Blöcke 1:1 aus der Welt ausgelesen; oben Iso-Ansicht, unten Seitenansicht.',
              f'selftest_{k}.png')


if __name__ == '__main__':
    main()
