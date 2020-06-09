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


class ChallengeRating:
    def __init__(self, name, monsters, xp):
        self.xp = xp
        self.monsters = monsters
        self.name = name

    def get_random_creature(self):
        return random.choice(self.monsters) if len(self.monsters) > 0 else None


class LootOptionItem:
    def __init__(self, value, weighting, enabled, metadata):
        self.weighting = weighting
        self.value = value
        self.enabled = enabled
        self.metadata = metadata

    def get_weighting_value(self):
        return self.enabled * self.weighting


class LootOption:
    def __init__(self, name):
        self.name = name
        self.loot_options = []

    def add_item(self, loot_option_item):
        item_weighting = loot_option_item.get_weighting_value()
        for i in range(item_weighting):
            self.loot_options.append(loot_option_item)
        random.shuffle(self.loot_options)

    def get_random_item(self):
        return random.choice(self.loot_options)


class PrayerStone(LootOptionItem):
    def __init__(self, value, weighting, enabled, metadata, levels, owner=None, progress=0):
        LootOptionItem.__init__(self, value, weighting, enabled, metadata)
        self.levels = levels
        self.progress = progress
        self.owner = owner

    def get_next(self):
        first_mod = "\t" + self._get_offset_option(0)
        if len(self.levels) > (1 + self.progress):
            return first_mod + "\nOR:\n\t" + self._get_offset_option(1)
        return first_mod

    def _get_offset_option(self, n):
        index = self.progress + n
        return self.levels[index] if index < len(self.levels) else None
