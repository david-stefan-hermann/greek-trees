"""JSON for the olive, the fig, the arbutus berry and the mulberry: the twig blocks that hang under the leaves
(blockstate, cross models, loot), the fruit items, the olive pit and the extra loot tables the mod hangs into the
azalea, mangrove and jungle leaf tables. Called from make_resources.py."""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from make_dates import leaf_fruit_table, ripe_drops  # noqa: E402
from recipes import PALE_WOOD, ingredient, recipe_unlock  # noqa: E402

NS = 'greektrees'

# twig block, fruit item, leaf table, block that marks the tree's leaves (its wood; for the fig, whose wood and
# leaves are the cypress's, the fig twig), English and German names (fruit, twig)
FRUITS = [
    ('olive_twig', 'olive', 'olive_leaf_olives', 'minecraft:oak_wood', ('Olive', 'Olive'), ('Olive Twig', 'Olivenzweig')),
    ('fig_twig', 'fig', 'fig_leaf_figs', f'{NS}:fig_twig', ('Fig', 'Feige'), ('Fig Twig', 'Feigenzweig')),
    ('arbutus_twig', 'arbutus_berry', 'strawberry_tree_leaf_berries', 'minecraft:stripped_acacia_wood',
     ('Arbutus Berry', 'Erdbeerbaumfrucht'), ('Arbutus Twig', 'Erdbeerbaumzweig')),
    # Mulberries come in three kinds, one per tree. The leaves are jungle leaves like the palms', told apart by the
    # wood of their trunk (vanilla jungle trees are made of logs); the kind by the tree's own twigs.
    ('black_mulberry_twig', 'black_mulberry', 'black_mulberry_leaf_mulberries',
     [PALE_WOOD, f'{NS}:black_mulberry_twig'], ('Black Mulberry', 'Schwarze Maulbeere'),
     ('Black Mulberry Twig', 'Schwarzer Maulbeerzweig')),
    ('white_mulberry_twig', 'white_mulberry', 'white_mulberry_leaf_mulberries',
     [PALE_WOOD, f'{NS}:white_mulberry_twig'], ('White Mulberry', 'Weiße Maulbeere'),
     ('White Mulberry Twig', 'Weißer Maulbeerzweig')),
    ('red_mulberry_twig', 'red_mulberry', 'red_mulberry_leaf_mulberries',
     [PALE_WOOD, f'{NS}:red_mulberry_twig'], ('Red Mulberry', 'Rote Maulbeere'),
     ('Red Mulberry Twig', 'Roter Maulbeerzweig')),
]


def item(write, name):
    write(f'assets/{NS}/models/item/{name}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/{name}'}})


def write_all(write):
    lang = {}
    for twig, fruit, leaf_table, wood, fruit_names, twig_names in FRUITS:
        # the twig's stage property is "stage", not "age" (see HangingFruitBlock.STAGE)
        write(f'assets/{NS}/blockstates/{twig}.json',
              {'variants': {f'stage={a}': {'model': f'{NS}:block/{twig}_stage{a}'} for a in range(3)}})
        for a in range(3):
            write(f'assets/{NS}/models/block/{twig}_stage{a}.json',
                  {'parent': 'minecraft:block/cross', 'textures': {'cross': f'{NS}:block/{twig}_stage{a}'}})
        item(write, fruit)
        write(f'data/{NS}/loot_table/blocks/{twig}.json', ripe_drops(f'{NS}:{twig}', f'{NS}:{fruit}', 'stage'))
        write(f'data/{NS}/loot_table/blocks/{leaf_table}.json', leaf_fruit_table(leaf_table, f'{NS}:{fruit}', wood))
        lang[f'item.{NS}.{fruit}'] = fruit_names
        lang[f'block.{NS}.{twig}'] = twig_names
    item(write, 'olive_pit')
    lang[f'item.{NS}.olive_pit'] = ('Olive Pit', 'Olivenkern')
    item(write, 'sharpened_olive_pit')
    lang[f'item.{NS}.sharpened_olive_pit'] = ('Sharpened Olive Pit', 'Geschärfter Olivenkern')
    # two pits on top of each other
    write(f'data/{NS}/recipe/sharpened_olive_pit.json', {
        'type': 'minecraft:crafting_shaped', 'category': 'equipment',
        'pattern': ['#', '#'], 'key': {'#': ingredient(f'{NS}:olive_pit')}, 'result': {'id': f'{NS}:sharpened_olive_pit'}})
    recipe_unlock(write, f'{NS}:sharpened_olive_pit', [f'{NS}:olive_pit'])
    lang[f'entity.{NS}.olive_pit'] = ('Olive Pit', 'Olivenkern')
    lang[f'itemGroup.{NS}'] = ('Greek Trees', 'Griechische Bäume')
    return lang
