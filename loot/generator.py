import json
import logging
import random
import readline
import sys
from functools import reduce
from os import sep, path
from pprint import PrettyPrinter
from typing import *
from typing import Dict, Any, List
from enum import Enum

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
        self.weapons: LootOptions = LootController._create_loot_option("weapon", do_flush)
        self.armours: LootOptions = LootController._create_loot_option("armour", do_flush)
        self.rings: LootOptions = LootController._create_loot_option("ring", do_flush)
        self.enchants: LootOptions = LootController._create_enchants(do_flush)
        self.consumables: LootOptions = LootController._create_loot_option("consumable", do_flush)
        self.challenge_ratings: Dict[str, Dict[str, Any]] = LootController._load_challenge_ratings(do_flush)
        self.all_crs = list(self.challenge_ratings.keys())

        relics = LootController._create_relics(do_flush)
        self.found_relics: Dict[str, Dict[str, Any]] = relics[0]
        self.unfound_relics: Dict[str, Dict[str, Any]] = relics[1]

    class ItemLevelUpOption(Enum):
        UPGRADE_EXISTING = 1
        NEW_RANDOM_MODS = 2
        NEW_RELIC_MOD = 3
        NEGATIVE_MODS = 4

    def reload_data(self):
        self.__init__(True)

    def level_up_prayer_path(self):
        started_paths = filter(
            lambda prayer_path: prayer_path['owner'] is not None
                                and prayer_path['enabled']
                                and prayer_path['progress'] != 10, self.prayer_paths)
        path_owners = dict((prayer_path['owner'], prayer_path) for prayer_path in started_paths)

        if not path_owners:
            logging.warning("No paths to level")
            return None

        owners = set(path_owners.keys())
        chosen_owner = LootController._take_input("Which owner's path do you want to level?", owners)
        if not chosen_owner:
            return None

        selected_path = path_owners[chosen_owner]
        progress = selected_path["progress"]
        levels: List[str] = selected_path["levels"]
        next_two = levels[progress:min(len(levels), progress + 2)]
        level_options = reduce(lambda next_level, level_after_next: "%s\n\tOR\n%s" % (next_level, level_after_next),
                               next_two)
        # TODO: persist chosen progression
        return "(%s)\n%s" % (selected_path["value"], level_options)

    def level_up_relic_by_choice(self):
        relic_choice = LootController._take_input("Which relic do you want to level?",
                                                  set(self.found_relics.keys()))
        if not relic_choice:
            return None

        return self._level_up_relic(self.found_relics[relic_choice])

    def _level_up_relic(self, relic: Dict[str, Any]):
        points_allowed_for_next_level: int = (relic["level"] + 1) * 10
        current_points = LootController._calculate_total_mod_values(relic["existing"])
        points_remaining = points_allowed_for_next_level - current_points
        if points_remaining < 0:
            options: List[LootController.ItemLevelUpOption] =\
                [LootController.ItemLevelUpOption.NEGATIVE_MODS for _ in range(3)]
            upgradeable_mods = list()
        else:
            options: List[LootController.ItemLevelUpOption] = [
                LootController.ItemLevelUpOption.NEW_RANDOM_MODS,
                LootController.ItemLevelUpOption.NEW_RELIC_MOD
            ]
            # TODO: load relics such that mods always contain "level" (d: 1),
            #  "upgradeable" (d: true), "points" (d: 10), level_up_points (d: points)
            upgradeable_mods = list(filter(lambda existing_mod: existing_mod["upgradeable"], relic["existing"]))
            third_choice_options = [
                LootController.ItemLevelUpOption.NEW_RANDOM_MODS,
                LootController.ItemLevelUpOption.NEW_RELIC_MOD
            ]
            if upgradeable_mods:
                if random.randint(0, 1):
                    options[random.randint(1, 2)] = LootController.ItemLevelUpOption.UPGRADE_EXISTING
                if len(upgradeable_mods) > 1:
                    third_choice_options.append(LootController.ItemLevelUpOption.UPGRADE_EXISTING)
            options.append(random.choice(third_choice_options))

        chosen_mods = set()
        for option in options:
            chosen_mod = None
            while not chosen_mod or chosen_mod in chosen_mods:
                chosen_mod = self._get_relic_upgrade_option(relic, option, upgradeable_mods, points_remaining)
            chosen_mods.add(chosen_mod)

        return reduce(lambda mod1, mod2: "%s\n\tOR\n%s" % (mod1, mod2),
                      map(lambda mod: mod["value"], chosen_mods))

    def _get_relic_upgrade_option(self, relic: Dict[str, Any],
                                  option: ItemLevelUpOption,
                                  upgradeable_mods: LootOptions,
                                  points_remaining: int) -> LootOptions:
        if option == LootController.ItemLevelUpOption.NEGATIVE_MODS:
            base = self.find_base_item(relic["base"])
            valid_enchants = self.get_valid_enchants_for_weapon(base) if relic["base"] == "weapon"\
                else self.get_valid_enchants_for_armour(base)
            return LootController._get_negative_enchants_totalling(valid_enchants, points_remaining)

        if option == LootController.ItemLevelUpOption.NEW_RANDOM_MODS:
            base = self.find_base_item(relic["base"])
            valid_enchants = self.get_valid_enchants_for_weapon(base) if relic["base"] == "weapon"\
                else self.get_valid_enchants_for_armour(base)
            return LootController._get_enchants_totalling(valid_enchants, points_remaining)

        if option == LootController.ItemLevelUpOption.NEW_RELIC_MOD:
            return [random.choice(relic["available"])]

        if option == LootController.ItemLevelUpOption.UPGRADE_EXISTING:
            added_total = 0
            mods_to_upgrade = []
            upgradeable_mods = set(upgradeable_mods)
            while added_total <= points_remaining:
                mod = random.choice(upgradeable_mods)
                mods_to_upgrade.append(mod)
                added_total += mod["level_up_points"]
            return mods_to_upgrade

    def find_base_item(self, base_name: str) -> Optional[LootOption]:
        matching_items = list(filter(lambda base: base["name"] == base_name, self.weapons + self.armours))
        if not matching_items:
            logging.warning("Could not find base item %s" % base_name)
            return None
        return matching_items[0]

    def get_new_relic(self):
        keys = list(self.unfound_relics.keys())
        randomly_chosen_key = keys[random.randint(0, len(keys) - 1)]
        relic = self.unfound_relics[randomly_chosen_key]
        return str(relic)

    def get_random_creature_of_cr(self, max_cr) -> Optional[str]:
        cr, cr_message = max_cr, max_cr
        while True:
            if cr == "":
                cr_message = "unspecified"
                cr = random.choice(list(self.challenge_ratings.keys()))
            if cr not in self.challenge_ratings:
                logging.warning("'%s' is not a valid CR option" % cr)
                return None
            monsters = self.challenge_ratings[cr]["monsters"]
            creature = random.choice(monsters) if monsters else None
            if creature is None:
                cr = str(int(cr) - 1)  # Not protecting against 0.125/0.25/0.5 because those have creatures
            else:
                if cr != max_cr:
                    logging.warning("Creature is of CR %s instead of %s" % (cr, cr_message))
                return creature

    def get_amulet(self) -> str:
        allowed_crs = ['0', '0.125', '0.25', '0.5', '1', '2', '3', '4']
        weightings = [0.05, 0.05, 0.05, 0.1, 0.25, 0.25, 0.15, 0.1]
        amulet_max_cr = random.choices(population=allowed_crs, weights=weightings, k=1)[0]
        return "Amulet CR: %s\n\tCreature: %s" % (amulet_max_cr, self.get_random_creature_of_cr(amulet_max_cr))

    def get_mundane(self):
        is_weapon = random.randint(1, 100) > 66
        options: LootOptions = self.weapons if is_weapon else self.armours
        return random.choice(options)["name"]

    def get_ring(self):
        return random.choice(self.rings)

    def get_prayer_stone(self):
        stone_options = set(map(lambda prayer_path: prayer_path["value"], self.prayer_paths))
        return random.choice(stone_options)

    def get_consumable(self):
        return LootController._get_multiple_items(random.sample(self.consumables),
                                                  lambda: random.randint(1, 4))

    def get_weapon_enchant(self):
        # TODO: reimplement
        enchantment = random.choice(self.enchants)
        if "armour" in enchantment.metadata:
            return self.get_weapon_enchant()
        return enchantment.value

    def get_valid_enchants_for_weapon(self, weapon: LootOption) -> LootOptions:
        return list(filter(lambda enchant:
                           (not enchant.get("metadata") or "weapon" in enchant["metadata"])
                           and LootController._is_compatible(weapon, enchant, "traits")
                           and LootController._is_compatible(weapon, enchant, "damage_types")
                           and LootController._is_compatible(weapon, enchant, "proficiency")
                           and LootController._is_compatible(weapon, enchant, "type"),

                           self.enchants))

    def get_valid_enchants_for_armour(self, armour: LootOption) -> LootOptions:
        return list(filter(lambda enchant:
                           (not enchant.get("metadata") or "armour" in enchant["metadata"])
                           and LootController._is_compatible(armour, enchant, "disadvantaged_stealth")
                           and LootController._is_compatible(armour, enchant, "type"),

                           self.enchants))

    def get_armour_enchant(self):
        enchantment = random.choice(self.enchants)
        if "weapon" in enchantment.metadata:
            return self.get_weapon_enchant()
        return enchantment.value

    def get_crafting_item(self):
        return LootController._get_multiple_items(random.choice(self.crafting_items),
                                                  lambda: random.randint(1, 3))

    @staticmethod
    def _calculate_total_mod_values(existing_mods: LootOptions) -> int:
        return sum(map(lambda mod: mod["points"] + ((mod["level"] - 1) * mod["level_up_points"]), existing_mods))

    @staticmethod
    def _is_compatible(item: LootOption, enchant: LootOption, field: str) -> bool:
        not_field = "not_%s" % field
        item_field_value = item[field]
        if isinstance(item_field_value, list):
            return not enchant.get(field) or not set(enchant[field]).isdisjoint(item_field_value) \
                   and not enchant.get(not_field) or set(enchant[not_field]).isdisjoint(item_field_value)
        else:
            return not enchant.get(field) or enchant[field] == item_field_value \
                   and not enchant.get(not_field) or enchant[field] != item_field_value

    def get_enchanted_item_totalling(self, n: int):
        base_type, valid_enchants = self.get_item_and_enchant_list()
        return LootController._get_enchants_totalling(valid_enchants, n)

    def get_negatively_enchanted_item(self, n: int):
        base_type, valid_enchants = self.get_item_and_enchant_list()
        return "Base: %s\nEnchants:%s" % (base_type, LootController._get_negative_enchants_totalling(valid_enchants, n))

    def get_item_and_enchant_list(self) -> Tuple[LootOption, LootOptions]:
        is_weapon = random.randint(1, 100) > 66
        options: LootOptions = self.weapons if is_weapon else self.armours
        base_type: LootOption = random.choice(options)
        valid_enchants = self.get_valid_enchants_for_weapon(base_type) if is_weapon \
            else self.get_valid_enchants_for_armour(base_type)
        return base_type, valid_enchants

    @staticmethod
    def _get_multiple_items(item, randomisation_function):
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

    @staticmethod
    def _get_enchants_totalling(valid_enchants: LootOptions, total: int):
        current_total = 0
        enchants = []
        while total >= current_total:
            enchant = random.choice(valid_enchants)
            current_total += enchant["points"]
            enchants.append(enchant)
        return enchants

    @staticmethod
    def _get_negative_enchants_totalling(valid_enchants: LootOptions, total: int):
        current_total = 0
        valid_negative_enchants = list(filter(lambda enchant: enchant["points"] < 0, valid_enchants))
        enchants = []
        while total < current_total:
            negative_enchant = random.choice(valid_negative_enchants)
            current_total += negative_enchant["points"]
            enchants.append(negative_enchant)
        return enchants

    @staticmethod
    def _load_challenge_ratings(do_flush) -> Dict[str, Dict[str, Any]]:
        file_contents = LootController._load_file_contents("monster", do_flush)
        return json.loads(file_contents)

    @staticmethod
    def _create_loot_option(name, do_flush) -> LootOptions:
        return LootController._load_with_defaults(name, do_flush,
                                                  {
                                                      "enabled": True,
                                                      "metadata": []
                                                  })

    @staticmethod
    def _create_prayer_paths(do_flush: bool = False) -> LootOptions:
        paths = LootController._create_loot_option("prayer_path", do_flush)
        defaults = {
            "enabled": True,
            "owner": None,
            "progress": 0
        }
        return list(map(lambda option: {**defaults, **option}, paths))

    @staticmethod
    def _create_enchants(do_flush=False) -> LootOptions:
        enchants = LootController._create_loot_option("enchant", do_flush)
        defaults = {
            "enabled": True,
            "points": 10
        }
        return list(map(lambda option: {**defaults, **option}, enchants))

    @staticmethod
    def _create_relics(do_flush=False) -> Tuple[Dict[str, Dict[str, Any]], Dict[str, Dict[str, Any]]]:
        enchants = LootController._create_loot_option("relic", do_flush)
        defaults = {
            "enabled": True,
            "found": False,
            "level": 1
        }
        all_relics = list(map(lambda option: {**defaults, **option}, enchants))
        found = dict()
        unfound = dict()
        for relic in all_relics:
            if not relic["enabled"]:
                continue
            target_dict = found if relic["found"] else unfound
            target_dict[relic["name"]] = relic
        return found, unfound

    @staticmethod
    def _load_with_defaults(filename: str, do_flush: bool, defaults: LootOption) -> LootOptions:
        file_contents = LootController._load_file_contents(filename, do_flush)
        dicts = json.loads(file_contents)
        return list(map(lambda option: {**defaults, **option}, dicts))

    @staticmethod
    def _load_file_contents(name, do_flush):
        with open(DATA_DIR + name + ".json") as data_file:
            if do_flush:
                data_file.flush()
                return LootController._load_file_contents(name, False)
            return data_file.read()

    @staticmethod
    def _take_input(prompt: str, options: Collection[str]) -> Optional[str]:
        options: Set[str] = options if isinstance(options, set) else set(options)
        logging.info("Input Options: %s" % options)
        readline.set_completer(Completer(options).complete)
        choice: str = input("\n%s " % prompt)
        readline.set_completer(lambda text, state: None)
        if choice not in options:
            logging.warning("%s is not a valid option (%s)" % (choice, options))
            return None
        return choice


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


def define_action_map(mapped_loot_controller) -> Dict[int, Callable[[], str]]:  # TODO: move to LootController __init__
    return {
        loot_types.LootType.negatively_enchanted.value:
            lambda: mapped_loot_controller.get_negatively_enchanted_item(25),
        loot_types.LootType.mundane.value: mapped_loot_controller.get_mundane,
        loot_types.LootType.consumable.value: mapped_loot_controller.get_consumable,
        loot_types.LootType.low_gold.value: lambda: str(random.randint(40, 50)) + " gold",
        loot_types.LootType.ring.value: lambda: "Ring: " + mapped_loot_controller.get_ring(),
        loot_types.LootType.single_enchant_item.value: lambda: mapped_loot_controller.get_enchanted_item_totalling(1),
        loot_types.LootType.amulet.value: mapped_loot_controller.get_amulet,
        loot_types.LootType.double_enchant_item.value: lambda: mapped_loot_controller.get_enchanted_item_totalling(2),
        loot_types.LootType.triple_enchant_item.value: lambda: mapped_loot_controller.get_enchanted_item_totalling(3),
        loot_types.LootType.crafting_item.value: mapped_loot_controller.get_crafting_item,
        loot_types.LootType.prayer_stone.value: lambda: "Prayerstone: " + mapped_loot_controller.get_prayer_stone(),
        loot_types.LootType.relic.value: mapped_loot_controller.get_new_relic,
        21: mapped_loot_controller.get_weapon_enchant,
        22: mapped_loot_controller.get_armour_enchant,
        24: lambda: do_continuously(mapped_loot_controller.get_random_creature_of_cr, "Monster CR:"),
        25: mapped_loot_controller.level_up_relic_by_choice,
        26: mapped_loot_controller.level_up_prayer_path,
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
            roll = random.randint(1, 20)
            print("Random roll: " + str(roll) + ", (" + str(loot_action_map[roll]) + ")")
        elif roll < 0:
            exit(0)
        print(loot_action_map.get(roll,
                                  lambda: str(roll) + " is not a normal loot option, checking extra options")())

        if roll == 28:
            loot_controller = LootController(True)
            loot_action_map = define_action_map(loot_controller)
            logging.info("Reloaded loot from files")
        elif roll > 30:  # TODO: better num
            print_options()


if __name__ == "__main__":
    # TODO: nicer format
    logging.basicConfig(stream=sys.stdout, level=logging.INFO,
                        format='[%(levelname)s] %(asctime)-15s %(message)s',
                        datefmt='%H:%M:%S')
    generate_loot()
