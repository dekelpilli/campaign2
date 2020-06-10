from enum import IntEnum


class LootType(IntEnum):
    """
    1 = negative enchant
    2 = mundane
    3 = consumable
    4 = low gold (40-50)?

    x = medium gold (100-200)
    x = rings (wtb ring ideas)
    x = amulet
    x = crafting
    x = magic item (more than one slot for different total allowed mod points) - some with guaranteed not negative

    13 = reroll with advantage? https://anydice.com/program/16a1

    18 = artefact
    19 = prayer
    20 = relic
    """
    negatively_enchanted = 1
    mundane = 2
    consumable = 3  # too low?

    ring = 4  # TODO: redo rings
    low_gold = 5
    single_enchant_item = 6
    double_enchant_item = 7
    triple_enchant_item = 9

    crafting_item = 13
    amulet = 14
    artefact = 18
    prayer_stone = 19
    relic = 20


LOOT_TYPES = dict()
for loot_type in LootType:
    LOOT_TYPES[loot_type.value] = loot_type
