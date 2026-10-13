"""Recipe-book unlocks for the recipes of make_resources.py and make_fruits.py."""

NS = 'greektrees'
# Minecraft 1.21.1 has no pale oak: the trunk of the mulberry and the willow is acacia wood, grey as well
# (TreeShapes.PALE_WOOD). A birch sapling stands in for the pale oak sapling in their recipes: with an acacia sapling
# the mulberry's recipe would be the strawberry tree's.
PALE_WOOD = 'minecraft:acacia_wood'
PALE_SAPLING = 'minecraft:birch_sapling'


def ingredient(item):
    """An ingredient the way 1.21.1 reads it: an object naming the item or the tag."""
    return {'tag': item[1:]} if item.startswith('#') else {'item': item}


def recipe_unlock(write, recipe_id, items):
    """Recipe-book unlock: picking up any of the items unlocks the recipe."""
    criteria = {'has_the_recipe': {'conditions': {'recipe': recipe_id}, 'trigger': 'minecraft:recipe_unlocked'}}
    for item in dict.fromkeys(items):
        criteria['has_' + item.split(':')[1]] = {'conditions': {'items': [{'items': item}]},
                                                 'trigger': 'minecraft:inventory_changed'}
    write(f'data/{NS}/advancement/recipes/misc/{recipe_id.split(":")[1]}.json', {
        'parent': 'minecraft:recipes/root',
        'criteria': criteria,
        'requirements': [list(criteria)],
        'rewards': {'recipes': [recipe_id]},
    })
