"""JSON for the date (item) and the date cluster (block): blockstate, models, loot tables, and the extra loot
table that the mod hangs into minecraft:blocks/jungle_leaves. Called from make_resources.py."""
import sys
import os

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import date_cluster  # noqa: E402

NS = 'greektrees'
FACING_Y = {'south': 0, 'west': 90, 'north': 180, 'east': 270}
NOT_SHEARS_OR_SILK = {
    'type': 'minecraft:inverted',
    'term': {'type': 'minecraft:any_of', 'terms': [
        {'type': 'minecraft:match_tool', 'predicate': {'items': 'minecraft:shears'}},
        {'type': 'minecraft:match_tool', 'predicate': {'predicates': {'minecraft:enchantments': [
            {'enchantments': 'minecraft:silk_touch', 'levels': {'min': 1}}]}}},
    ]},
}
# chance per broken leaf without Fortune and with Fortune I-IV, three times the first version's
LEAF_CHANCES = [0.12, 0.135, 0.15, 0.2, 0.45]


def leaf_fruit_table(name, item, wood):
    """Extra pool the mod hangs into a vanilla leaf table: the fruit now and then, only near the given wood (a block
    id, or a list of ids that must all be near)."""
    near = [{'type': f'{NS}:near_block', 'block': b} for b in ([wood] if isinstance(wood, str) else wood)]
    return {
        'type': 'minecraft:block',
        'pools': [{'rolls': 1,
                   'condition': {'type': 'minecraft:all_of', 'terms': [NOT_SHEARS_OR_SILK] + near},
                   'entries': [{'type': 'minecraft:item', 'name': item,
                                'condition': {'type': 'minecraft:table_bonus', 'enchantment': 'minecraft:fortune',
                                              'chances': LEAF_CHANCES},
                                'modifier': {'type': 'minecraft:explosion_decay'}}]}],
        'random_sequence': f'{NS}:blocks/{name}',
    }


def ripe_drops(block, item, stage='age'):
    """A ripe fruit block (its stage property at 2) gives 2-3 of its fruit, an unripe one its fruit back, like
    cocoa."""
    return {
        'type': 'minecraft:block',
        'pools': [{'rolls': 1, 'entries': [{
            'type': 'minecraft:item', 'name': item,
            'modifier': [
                {'type': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': 2, 'max': 3},
                 'condition': {'type': 'minecraft:match_block', 'blocks': block, 'state': {stage: '2'}}},
                {'type': 'minecraft:explosion_decay'},
            ]}]}],
        'random_sequence': f'{NS}:blocks/{block.split(":")[1]}',
    }


def _element(e):
    faces = {f: {'uv': list(e.uv(f)), 'texture': '#cluster'} for f in ('north', 'south', 'east', 'west', 'up', 'down')}
    el = {'from': [round(float(v), 3) for v in e.lo], 'to': [round(float(v), 3) for v in e.hi], 'faces': faces}
    if any(e.angles):
        el['rotation'] = {'origin': [round(float(v), 3) for v in e.origin],
                          'x': e.angles[0], 'y': e.angles[1], 'z': e.angles[2]}
    return el


def write_all(write):
    problems = date_cluster.check()
    assert not problems, problems
    variants = {}
    for age in range(3):
        for facing, y in FACING_Y.items():
            v = {'model': f'{NS}:block/date_cluster_stage{age}'}
            if y:
                v['y'] = y
            variants[f'age={age},facing={facing}'] = v
    write(f'assets/{NS}/blockstates/date_cluster.json', {'variants': variants})
    for age, cluster in enumerate(date_cluster.STAGES):
        tex = f'{NS}:block/date_cluster_stage{age}'
        write(f'assets/{NS}/models/block/date_cluster_stage{age}.json',
              {'ambientocclusion': False, 'textures': {'particle': tex, 'cluster': tex},
               'elements': [_element(e) for e in cluster.elements()]})
    write(f'assets/{NS}/items/date.json', {'model': {'type': 'minecraft:model', 'model': f'{NS}:item/date'}})
    write(f'assets/{NS}/models/item/date.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/date'}})
    write(f'data/{NS}/loot_table/blocks/date_cluster.json', ripe_drops(f'{NS}:date_cluster', f'{NS}:date'))
    # hung into jungle leaves by the mod; only fires near jungle wood, which is what the palms are made of
    write(f'data/{NS}/loot_table/blocks/palm_leaf_dates.json',
          leaf_fruit_table('palm_leaf_dates', f'{NS}:date', 'minecraft:jungle_wood'))
    return {'item.greektrees.date': ('Date', 'Dattel'), 'block.greektrees.date_cluster': ('Date Cluster', 'Dattelrispe')}
