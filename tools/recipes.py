"""Recipe-book unlocks for the recipes of make_resources.py and make_fruits.py."""

NS = 'greektrees'


def recipe_unlock(write, recipe_id, items):
    """Recipe-book unlock: picking up any of the items unlocks the recipe."""
    criteria = {'has_the_recipe': {'conditions': {'recipes': recipe_id}, 'trigger': 'minecraft:recipe_unlocked'}}
    for item in dict.fromkeys(items):
        criteria['has_' + item.split(':')[1]] = {'conditions': {'items': [{'items': item}]},
                                                 'trigger': 'minecraft:inventory_changed'}
    write(f'data/{NS}/advancement/recipes/misc/{recipe_id.split(":")[1]}.json', {
        'parent': 'minecraft:recipes/root',
        'criteria': criteria,
        'requirements': [list(criteria)],
        'rewards': {'recipes': [recipe_id]},
    })
