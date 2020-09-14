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
    common_magic_item = 3
    consumable = 4
    low_gold = 5

    low_enchant_item = 10
    medium_enchant_item = 11
    high_enchant_item = 12
    crafting_item = 13
    amulet = 14
    artefact = 18
    prayer_stone = 19
    relic = 20

    ring = 99  # TODO: redo rings


LOOT_TYPES = {loot_type.value: loot_type for loot_type in LootType}
