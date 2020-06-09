import random
from enum import IntEnum


class LootType(IntEnum):
    junk = 1  # negatively enchanted item
    mundane = 2
    consumable = 3
    ring = 4  # TODO: redo rings
    low_gold = 5
    single_enchant_item = 6
    double_enchant_item = 7
    amulet = 8
    triple_enchant_item = 9
    crafting_item = 10
    prayer_stone = 11
    relic = 12  # plus reroll
    prayer_stone_2 = -1  # disabled


LOOT_TYPES = dict()
for loot_type in LootType:
    LOOT_TYPES[loot_type.value] = loot_type


class Relic:
    def __init__(self, name, relic_type, existing, available, found, enabled, level):
        self.level = level
        self.enabled = enabled
        self.found = found
        self.available = available
        self.existing = existing
        self.type = relic_type
        self.name = name

    def __str__(self):
        base_description = self.name + " (" + self.type + "):"
        existing_mod_description = ""
        for existing_mod in self.existing:
            existing_mod_description += "\n\t" + existing_mod.value
        return base_description + existing_mod_description


class RelicMod:
    def __init__(self, value, upgradeable, comment=None):
        self.upgradeable = upgradeable
        self.value = value
        self.comment = comment
