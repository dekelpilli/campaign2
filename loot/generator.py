import json
import logging
import random
import readline
import sys
from os import sep, path
from pprint import PrettyPrinter
from typing import List, Dict, Any

import loot_types
from input_completer import Completer

DATA_DIR = path.dirname(path.realpath(sys.argv[0])) + sep + "data" + sep
LootOptions = List[Dict[str, Any]]
LootOption = Dict[str, Any]


class LootController:
    def __init__(self, do_flush=False):
        logging.info("Loading...")
        self.prayer_paths: LootOptions = LootController._create_prayer_paths(do_flush)
        self.crafting_items: LootOptions = LootController._create_loot_option("crafting_item", do_flush)
        self.mundanes: LootOptions = LootController._create_loot_option("mundane", do_flush)
        self.rings: LootOptions = LootController._create_loot_option("ring", do_flush)
        self.enchants: LootOptions = LootController._create_loot_option("enchant", do_flush)
        self.consumables: LootOptions = LootController._create_loot_option("consumable", do_flush)
        self.challenge_ratings: Dict[str, Any] = LootController._get_file_contents("monster", do_flush)
        self.all_crs = list(self.challenge_ratings.keys())

        self.found_relics, self.unfound_relics = LootController._create_relics(do_flush)

    def level_up_prayer_path(self):
        prayer_paths_started = list(filter(
            lambda prayer_path: prayer_path['owner'] is not None
                                and prayer_path['enabled']
                                and prayer_path['progress'] != 10,
            self.prayer_paths))

        if not prayer_paths_started:
            logging.warning("No paths to level")
            return None

        owners = set(map(lambda prayer_stone: prayer_stone.owner, prayer_paths_started))
        readline.set_completer(Completer(owners).complete)
        logging.info("Found prayer paths belonging to these characters: %s" % owners)
        prayer_path_owner_choice = input("\nWhich owner's path do you want to level? ")
        readline.set_completer(lambda text, state: None)
        if prayer_path_owner_choice not in owners:
            return prayer_path_owner_choice + " is not a valid prayer path owner choice"

        selected_prayer_stone = list(filter(lambda prayer_stone: prayer_path_owner_choice == prayer_stone.owner,
                                            prayer_paths_started))[0]
        # TODO: recreate "get next" logic
        return "(%s)\n%s" % (selected_prayer_stone["value"], selected_prayer_stone.get_next())

    def level_up_relic_by_choice(self):
        found_relics = self._get_found_relics()
        if len(found_relics) == 0:
            return "No relics to level"

        readline.set_completer(Completer(found_relics).complete)
        print(found_relics)
        relic_choice = input("\nWhich relic do you want to level? ")
        readline.set_completer(lambda text, state: None)
        if relic_choice not in found_relics:
            return relic_choice + " is not a valid relic choice"

        return self._level_up_relic_by_name(relic_choice)

    def _level_up_relic_by_name(self, relic_name):
        relic = self.found_relics[relic_name]
        return self._level_up_relic(relic)

    def _level_up_relic(self, relic, num_choices=3):
        options = 2  # new random mod, new relic mod
        upgradeable_mods = []
        for existing_mod in relic.existing:
            if existing_mod.upgradeable:
                upgradeable_mods.append(existing_mod)
        if len(upgradeable_mods) > 0:
            options += 2  # double weighting

        output = "Options:\n\t"
        chosen_mods = set()
        while len(chosen_mods) < num_choices:
            chosen_mod = self._get_relic_upgrade_option(relic, random.randint(1, options), upgradeable_mods)
            if chosen_mod in chosen_mods:
                continue
            if len(chosen_mods) != 0:
                output += "\nOR\n\t"
            chosen_mods.add(chosen_mod)
            output += chosen_mod
        return output

    def _get_relic_upgrade_option(self, relic, option_id, upgradeable_mods):
        option_string = ""
        if option_id == 1:
            option_string += "New mod: "
            if relic.type == "weapon":
                return option_string + self.get_weapon_enchant()
            elif relic.type == "armour":
                return option_string + self.get_armour_enchant()
            elif relic.type == "ring":
                return option_string + self.get_ring()
            elif relic.type == "amulet":
                option_string = ""
                option_id = random.randint(2, 2 + min(1, len(upgradeable_mods)))

        if option_id == 2:
            option_string += "New Relic mod: "
            mod = random.choice(relic.available)
            return option_string + mod.value

        if option_id > 2:
            chosen_mod = random.choice(upgradeable_mods)
            option_string += "Upgrade existing mod: " + chosen_mod.value
            if chosen_mod.comment is not None:
                option_string += " (" + chosen_mod.comment + ")"
            return option_string

    def get_new_relic(self):
        keys = list(self.unfound_relics.keys())
        randomly_chosen_key = keys[random.randint(0, len(keys) - 1)]
        relic = self.unfound_relics[randomly_chosen_key]
        return str(relic)

    def _get_found_relics(self):
        return list(self.found_relics.keys())

    def get_min_random_cr(self, tries):
        crs = list(self.challenge_ratings.keys())
        cr = max(crs)
        for i in range(int(tries)):
            cr = min(random.choice(crs), cr)
        return cr

    def get_random_creature_of_cr(self, max_cr):
        cr, cr_message = max_cr, max_cr
        while True:
            if cr == "":
                cr_message = "unspecified"
                cr = random.choice(list(self.challenge_ratings.keys()))
            if cr not in self.challenge_ratings:
                logging.warning("'%s' is not a valid CR option" % cr)
                return None
            creature = self.challenge_ratings[cr]["monsters"].get_random_creature()
            if creature is None:
                cr = str(int(cr) - 1)  # Not protecting against 0.125/0.25/0.5 because those have creatures
            else:
                if cr != max_cr:
                    logging.warning("Creature is of CR %s instead of %s" % (cr, cr_message))
                return creature

    def get_amulet(self):
        max_allowed_cr_index = self.all_crs.index(str(random.randint(2, 4)))
        amulet_cr_capacity_idx = random.randint(2, max_allowed_cr_index)
        amulet_max_cr = self.all_crs[amulet_cr_capacity_idx]
        return "Amulet CR: " + amulet_max_cr + "\n\tCreature: " + self.get_random_creature_of_cr(amulet_max_cr)

    def get_mundane(self):
        is_weapon = random.randint(1, 100) > 66
        mundane_type = "weapon" if is_weapon else "armour"
        possible_mundanes = list(filter(lambda mundane: mundane_type in mundane.metadata, self.mundanes))
        return random.choice(possible_mundanes).value

    def get_ring(self):
        return random.choice(self.rings)

    def get_prayer_stone(self):
        stone_options = set(map(lambda prayer_path: prayer_path["value"], self.prayer_paths))  # TODO: move to init?
        return random.choice(stone_options)

    def get_enchant(self):
        return random.choice(self.enchants)

    def get_consumable(self):
        return LootController.get_multiple_items(random.sample(self.consumables),
                                                 lambda: random.randint(1, 4))

    def get_weapon_enchant(self):
        enchantment = random.choice(self.enchants)
        if "armour" in enchantment.metadata:
            return self.get_weapon_enchant()
        return enchantment.value

    def get_armour_enchant(self):
        enchantment = random.choice(self.enchants)
        if "weapon" in enchantment.metadata:
            return self.get_weapon_enchant()
        return enchantment.value

    def get_crafting_item(self):
        return LootController.get_multiple_items(random.choice(self.crafting_items),
                                                 lambda: random.randint(1, 3))

    @staticmethod
    def get_multiple_items(item, randomisation_function):
        amount = None
        for metadata_tag in item.metadata:
            old_function = randomisation_function
            if metadata_tag == "disadvantage":
                randomisation_function = lambda: min(old_function(), old_function())
            elif metadata_tag == "advantage":
                randomisation_function = lambda: max(old_function(), old_function())
            elif metadata_tag[0] == "x":
                multiplier = get_int_from_str(metadata_tag[1:], 1)
                randomisation_function = lambda: old_function() * multiplier
            else:
                potentially_static_value = get_int_from_str(metadata_tag)
                if potentially_static_value is not None:
                    amount = potentially_static_value

        if amount is None:
            amount = randomisation_function()
        return str(amount) + " " + item.value

    def get_n_enchanted_item(self, n: int):
        is_weapon = random.randint(1, 100) > 66
        mundane_type = "weapon" if is_weapon else "armour"
        # TODO: refactor tag system to allow more complex tagging
        possible_mundanes = list(filter(lambda mundane: mundane_type in mundane.metadata, self.mundanes))
        base_type = random.choice(possible_mundanes)
        enchant_generator_function = self.get_weapon_enchant if is_weapon else self.get_armour_enchant

        item_string = base_type.value
        for i in range(n):
            item_string += "\n\t" + enchant_generator_function()
        return item_string

    @staticmethod
    def _create_challenge_ratings(do_flush=False) -> Dict[str, str]:
        file_contents = LootController._get_file_contents("monster", do_flush)
        cr_dicts = json.loads(file_contents)
        crs = dict()
        for cr in cr_dicts:
            crs[cr] = loot_types.ChallengeRating(cr, cr_dicts[cr]["monsters"], cr_dicts[cr]["XP"])
        return crs

    @staticmethod
    def _create_loot_option(name, do_flush=False) -> LootOptions:
        return LootController._load_with_defaults(name, do_flush,
                                                  {
                                                      "enabled": True,
                                                      "metadata": []
                                                  })

    @staticmethod
    def _create_prayer_paths(do_flush=False) -> LootOptions:
        return LootController._load_with_defaults("prayer_path",
                                                  do_flush,
                                                  {
                                                      "enabled": True,
                                                      "owner": None,
                                                      "progress": 0
                                                  })

    @staticmethod
    def _load_with_defaults(filename: str, do_flush: bool, defaults: LootOption) -> LootOptions:
        file_contents = LootController._get_file_contents(filename, do_flush)
        dicts = json.loads(file_contents)
        return list(map(lambda option: {**defaults, **option}, dicts))

    @staticmethod
    def _get_file_contents(name, do_flush=False):
        with open(DATA_DIR + name + ".json") as data_file:
            if do_flush:
                data_file.flush()
                return LootController._get_file_contents(name)
            return data_file.read()

    @staticmethod
    def _create_relics(do_flush=False):
        file_contents = LootController._get_file_contents("relic", do_flush)
        relic_dicts = json.loads(file_contents)
        found = dict()
        unfound = dict()
        for relic_dict in relic_dicts:
            relic = LootController._create_relic(relic_dict)
            if not relic.enabled:
                continue
            target_dict = found if relic.found else unfound
            target_dict[relic.name] = relic
        return found, unfound

    @staticmethod
    def _create_relic(relic_dict):
        existing = []
        available = []
        for mod in relic_dict["existing"]:
            existing.append(LootController._create_relic_mod(mod))
        for mod in relic_dict["available"]:
            available.append(LootController._create_relic_mod(mod))
        return loot_types.Relic(relic_dict["name"],
                                relic_dict["type"],
                                existing,
                                available,
                                relic_dict.get("found", False),
                                relic_dict.get("enabled", True),
                                relic_dict.get("level", 1))

    @staticmethod
    def _create_relic_mod(relic_mod_dict: dict) -> loot_types.RelicMod:
        return loot_types.RelicMod(relic_mod_dict["value"],
                                   relic_mod_dict.get("upgradeable", True),
                                   relic_mod_dict.get("comment", None))


def get_int_from_str(string, default_integer=None):
    try:
        return int(string)
    except ValueError:
        return default_integer


def do_continuously(f, prompt):
    while True:
        entered = input("\n" + prompt + " ")
        if "-1" in entered:
            break
        print(f(entered))
    return ""


def print_options():  # TODO: move to LootController, print map from self
    pp = PrettyPrinter(indent=4)
    print("\n")
    pp.pprint(loot_types.LOOT_TYPES)
    print("\t13: Random weapon enchant")
    print("\t14: Random armour enchant")
    print("\t15: Random enchant")
    print("\t16: Reload loot")
    print("\t17: Creature of a given CR")
    print("\t18: Level a relic")
    print("\t19: Level a prayer path")
    print("\t20: Get min random CR")
    print("\t>19: Show this")


def define_action_map(mapped_loot_controller):  # TODO: move to LootController __init__
    prayer_stone_function = lambda: "Prayerstone: " + mapped_loot_controller.get_prayer_stone()
    return {
        loot_types.LootType.mundanes.value: mapped_loot_controller.get_mundane,
        loot_types.LootType.consumables.value: mapped_loot_controller.get_consumable,
        loot_types.LootType.low_gold.value: lambda: str(random.randint(30, 100)) + " gold",
        loot_types.LootType.ring.value: lambda: "Ring: " + mapped_loot_controller.get_ring(),
        loot_types.LootType.single_enchant_item.value: lambda: mapped_loot_controller.get_n_enchanted_item(1),
        loot_types.LootType.amulet.value: mapped_loot_controller.get_amulet,
        loot_types.LootType.double_enchant_item.value: lambda: mapped_loot_controller.get_n_enchanted_item(2),
        loot_types.LootType.triple_enchant_item.value: lambda: mapped_loot_controller.get_n_enchanted_item(3),
        loot_types.LootType.crafting_items.value: mapped_loot_controller.get_crafting_item,
        loot_types.LootType.prayer_paths.value: prayer_stone_function,
        loot_types.LootType.relic.value: mapped_loot_controller.get_new_relic,
        21: mapped_loot_controller.get_weapon_enchant,
        22: mapped_loot_controller.get_armour_enchant,
        23: mapped_loot_controller.get_enchant,
        24: lambda: do_continuously(mapped_loot_controller.get_random_creature_of_cr, "Monster CR:"),
        25: mapped_loot_controller.level_up_relic_by_choice,
        26: mapped_loot_controller.level_up_prayer_path,
        27: lambda: do_continuously(mapped_loot_controller.get_min_random_cr, "How many tries?")
    }


def generate_loot():
    loot_controller = LootController()
    loot_action_map = define_action_map(loot_controller)
    print_options()
    while True:
        readline.set_completer_delims(' \t\n;')
        readline.parse_and_bind("tab: complete")
        roll = get_int_from_str(input("\nLoot roll: "), 99)
        if roll == 0:
            roll = random.randint(1, 12)
            print("Random roll: " + str(roll) + ", (" + str(loot_action_map[roll]) + ")")
        elif roll < 0:
            exit(0)
        print(loot_action_map.get(roll,
                                  lambda: str(roll) + " is not a normal loot option, checking extra options")())

        if roll == 16:
            loot_controller = LootController(True)
            loot_action_map = define_action_map(loot_controller)
            logging.info("Reloaded loot from files")
        elif roll > 30:  # TODO: better num
            print_options()


if __name__ == "__main__":
    # TODO: nicer format
    logging.basicConfig(stream=sys.stdout, level=logging.INFO)
    generate_loot()
