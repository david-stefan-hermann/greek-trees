"""Writes the per-sapling JSON (blockstates, models, loot tables, tags, configured features,
recipes with their recipe-book unlocks)
and the language files. python tools/make_resources.py"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import make_book  # noqa: E402
import make_dates  # noqa: E402
import make_fruits  # noqa: E402
from recipes import PALE_SAPLING, ingredient, recipe_unlock  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, 'src', 'main', 'resources')
NS = 'greektrees'

# tree id, English name, German sapling name, German potted name, shapeless recipe (picking up any of its
# ingredients unlocks it in the recipe book)
TREES = [
    ('cypress', 'Cypress', 'Zypressensetzling', 'Eingetopfter Zypressensetzling',
     ['minecraft:spruce_sapling', 'minecraft:acacia_sapling']),
    ('olive', 'Olive', 'Olivenbaumsetzling', 'Eingetopfter Olivenbaumsetzling',
     ['minecraft:oak_sapling', 'minecraft:azalea_leaves', 'minecraft:apple']),
    ('fig', 'Fig', 'Feigensetzling', 'Eingetopfter Feigensetzling',
     ['minecraft:acacia_sapling', 'minecraft:acacia_sapling', 'minecraft:sweet_berries']),
    ('strawberry_tree', 'Strawberry Tree', 'Erdbeerbaumsetzling', 'Eingetopfter Erdbeerbaumsetzling',
     ['minecraft:acacia_sapling', 'minecraft:sweet_berries']),
    ('date_palm', 'Date Palm', 'Dattelpalmensetzling', 'Eingetopfter Dattelpalmensetzling',
     ['minecraft:jungle_sapling', 'minecraft:cocoa_beans']),
    ('mulberry', 'Mulberry', 'Maulbeerbaumsetzling', 'Eingetopfter Maulbeerbaumsetzling',
     [PALE_SAPLING, 'minecraft:sweet_berries']),
    ('weeping_willow', 'Weeping Willow', 'Trauerweidensetzling', 'Eingetopfter Trauerweidensetzling',
     [PALE_SAPLING, 'minecraft:azalea']),
    # grows only from sixteen saplings in a 4x4 square
    ('aries_oak', 'Aries Oak', 'Widdereichensetzling', 'Eingetopfter Widdereichensetzling',
     ['minecraft:dark_oak_sapling', 'minecraft:flowering_azalea', 'minecraft:glow_berries']),
]


# trees that also grow big from four saplings in a square (feature greektrees:large_<tree>)
SQUARE_TREES = ['date_palm', 'weeping_willow']


def write(rel, data):
    path = os.path.join(RES, *rel.split('/'))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write('\n')


def survives(name):
    return {'rolls': 1.0, 'entries': [{'type': 'minecraft:item', 'name': name}],
            'conditions': [{'condition': 'minecraft:survives_explosion'}]}


def main():
    en = {}
    de = {}
    saplings = []
    pots = []
    for tree, en_name, de_name, de_potted, recipe in TREES:
        s = f'{tree}_sapling'
        p = f'potted_{s}'
        saplings.append(f'{NS}:{s}')
        pots.append(f'{NS}:{p}')
        write(f'assets/{NS}/blockstates/{s}.json', {'variants': {'': {'model': f'{NS}:block/{s}'}}})
        write(f'assets/{NS}/blockstates/{p}.json', {'variants': {'': {'model': f'{NS}:block/{p}'}}})
        write(f'assets/{NS}/models/block/{s}.json', {'parent': 'minecraft:block/cross', 'textures': {'cross': f'{NS}:block/{s}'}})
        write(f'assets/{NS}/models/block/{p}.json',
              {'parent': 'minecraft:block/flower_pot_cross', 'textures': {'plant': f'{NS}:block/{s}'}})
        write(f'assets/{NS}/models/item/{s}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:block/{s}'}})
        write(f'data/{NS}/loot_table/blocks/{s}.json',
              {'type': 'minecraft:block', 'pools': [survives(f'{NS}:{s}')], 'random_sequence': f'{NS}:blocks/{s}'})
        write(f'data/{NS}/loot_table/blocks/{p}.json',
              {'type': 'minecraft:block', 'pools': [survives('minecraft:flower_pot'), survives(f'{NS}:{s}')],
               'random_sequence': f'{NS}:blocks/{p}'})
        write(f'data/{NS}/worldgen/configured_feature/{tree}.json', {'type': f'{NS}:{tree}', 'config': {}})
        write(f'data/{NS}/recipe/{s}.json', {
            'type': 'minecraft:crafting_shapeless', 'category': 'misc',
            'ingredients': [ingredient(i) for i in recipe], 'result': {'id': f'{NS}:{s}'}})
        recipe_unlock(write, f'{NS}:{s}', recipe)
        en[f'block.{NS}.{s}'] = f'{en_name} Sapling'
        en[f'block.{NS}.{p}'] = f'Potted {en_name} Sapling'
        de[f'block.{NS}.{s}'] = de_name
        de[f'block.{NS}.{p}'] = de_potted
    for tree in SQUARE_TREES:  # the big tree four saplings in a square grow into
        write(f'data/{NS}/worldgen/configured_feature/large_{tree}.json', {'type': f'{NS}:large_{tree}', 'config': {}})
    for module in (make_dates, make_fruits, make_book):
        for key, (en_text, de_text) in module.write_all(write).items():
            en[key] = en_text
            de[key] = de_text
    write('data/minecraft/tags/block/saplings.json', {'values': saplings})
    write('data/minecraft/tags/item/saplings.json', {'values': saplings})
    write(f'data/{NS}/tags/item/saplings.json', {'values': saplings})  # the guide book's recipe
    write('data/minecraft/tags/block/flower_pots.json', {'values': pots})
    write(f'assets/{NS}/lang/en_us.json', en)
    write(f'assets/{NS}/lang/de_de.json', de)


if __name__ == '__main__':
    main()
