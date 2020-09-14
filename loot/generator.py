import distutils.util
import json
import logging
import random
import readline
import sys
from enum import Enum
from functools import reduce
from os import sep, path
from pprint import PrettyPrinter
from typing import *
from typing import Dict, Any, List

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
        self.magic_items = LootController._create_loot_option("magic_item", do_flush)
        self.enchants: LootOptions = LootController._create_enchants(do_flush)
        self.consumables: LootOptions = LootController._create_loot_option("consumable", do_flush)
        self.monsters: Dict[str, List[str]] = LootController._load_challenge_ratings(do_flush)
        self.relics: LootOptions = LootController._create_relics(do_flush)

    class ItemLevelUpOption(Enum):
        UPGRADE_EXISTING = 1
        NEW_RANDOM_MODS = 2
        NEW_RELIC_MODS = 3
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

        chosen_owner = LootController._take_input("Which owner's path do you want to level?", set(path_owners))
        if not chosen_owner:
            return None

        selected_path = path_owners[chosen_owner]
        progress = selected_path["progress"]
        levels: List[str] = selected_path["levels"]
        next_two = levels[progress:min(len(levels), progress + 2)]
        level_options = reduce(lambda next_level, level_after_next: "%s\n\tOR\n%s" % (next_level, level_after_next),
                               next_two)
        level_selection = LootController._take_input_from_index("Which mod was chosen?", next_two)
        if level_selection:
            index, _ = level_selection
            selected_path["progress"] = progress + index + 1
            self._persist_prayer_paths()
        return "(%s)\n%s", selected_path["value"], level_options

    def _persist_prayer_paths(self):
        LootController._write_file("prayer_path", self.prayer_paths)

    def _persist_relics(self):
        LootController._write_file("relic", self.relics)

    def level_up_relic_by_choice(self):
        found_relics = filter(lambda relic: relic["found"] and relic["enabled"], self.relics)
        found_relic_names = {found_relic["name"]: found_relic for found_relic in found_relics}
        relic_choice = LootController._take_input_from_index("Which relic do you want to level?",
                                                             set(found_relic_names))

        if not relic_choice:
            return None

        _, chosen_relic = relic_choice
        return self._level_up_relic(found_relic_names[chosen_relic])

    def _level_up_relic(self, relic: Dict[str, Any]):
        next_relic_level = relic["level"] + 1
        points_allowed_for_next_level: int = next_relic_level * 10
        current_points = LootController._calculate_total_mod_values(relic["existing"])
        points_remaining = points_allowed_for_next_level - current_points
        if points_remaining < 0:
            options: List[LootController.ItemLevelUpOption] = \
                [LootController.ItemLevelUpOption.NEGATIVE_MODS for _ in range(3)]
            upgradeable_mods = list()
        else:
            options: List[LootController.ItemLevelUpOption] = [
                LootController.ItemLevelUpOption.NEW_RANDOM_MODS,
                LootController.ItemLevelUpOption.NEW_RELIC_MODS
            ]
            upgradeable_mods = list(filter(lambda existing_mod: existing_mod["upgradeable"], relic["existing"]))
            third_choice_options = [
                LootController.ItemLevelUpOption.NEW_RANDOM_MODS,
                LootController.ItemLevelUpOption.NEW_RELIC_MODS
            ]
            if upgradeable_mods:
                if random.randint(0, 1):
                    options[random.randint(1, 2)] = LootController.ItemLevelUpOption.UPGRADE_EXISTING
                if len(upgradeable_mods) > 1:
                    third_choice_options.append(LootController.ItemLevelUpOption.UPGRADE_EXISTING)
            options.append(random.choice(third_choice_options))

        option_mods = list()
        for option in options:
            option_mod = self._get_relic_upgrade_option(relic, option, upgradeable_mods, points_remaining)
            option_mods.append(option_mod)

        choice = LootController._take_input_from_index("Which mods were chosen?", option_mods)
        if choice:
            index, chosen_mods = choice
            relic["existing"] += chosen_mods
            if options[index] == LootController.ItemLevelUpOption.NEW_RELIC_MODS:
                for chosen_mod in chosen_mods:
                    relic["available"].remove(chosen_mod)
            relic["level"] = next_relic_level
            self._persist_relics()

    def _get_relic_upgrade_option(self, relic: Dict[str, Any],
                                  option: ItemLevelUpOption,
                                  upgradeable_mods: LootOptions,
                                  points_remaining: int) -> LootOptions:
        if option == LootController.ItemLevelUpOption.NEGATIVE_MODS:
            base = self.find_base_item(relic["base"])
            valid_enchants = self.get_valid_enchants_for_weapon(base) if relic["type"] == "weapon" \
                else self.get_valid_enchants_for_armour(base)
            return list(map(LootController._get_default_relic_mod,
                            LootController._get_negative_enchants_totalling(valid_enchants, points_remaining)))

        if option == LootController.ItemLevelUpOption.NEW_RANDOM_MODS:
            base = self.find_base_item(relic["base"])
            valid_enchants = self.get_valid_enchants_for_weapon(base) if relic["type"] == "weapon" \
                else self.get_valid_enchants_for_armour(base)
            return list(map(LootController._get_default_relic_mod,
                            LootController._get_enchants_totalling(valid_enchants, points_remaining)))

        if option == LootController.ItemLevelUpOption.NEW_RELIC_MODS:
            mods = {available_mod["value"]: available_mod for available_mod in relic["available"]}
            added_total = 0
            new_mods = []
            while added_total < points_remaining and mods.keys():
                mod = mods.get(random.choice(list(mods.keys())))
                new_mods.append(mod)
                mods.pop(mod["value"])
                added_total += mod["level_up_points"]
            return new_mods

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
        unfound_relics = list(filter(lambda potential_relic_option: not potential_relic_option["found"]
                                                                    and potential_relic_option["enabled"], self.relics))
        if not unfound_relics:
            logging.warning("No unfound relics exist")
            return
        relic = random.choice(unfound_relics)
        log_formatted_args(("Found relic %s", relic))
        if distutils.util.strtobool(LootController._take_input("Mark relic as found?", ["Yes", "No"], False)):
            relic["found"] = True
            self._persist_relics()

    def get_random_creature_of_cr(self, max_cr) -> Optional[str]:
        cr, cr_message = max_cr, max_cr
        while True:
            if cr == "":
                cr_message = "unspecified"
                cr = random.choice(list(self.monsters))
            if cr not in self.monsters:
                logging.warning("'%s' is not a valid CR option" % cr)
                return None
            monsters = self.monsters[cr]
            creature = random.choice(monsters) if monsters else None
            if creature is None:
                cr = str(int(cr) - 1)  # Not protecting against 0.125/0.25/0.5 because those have creatures
            else:
                if cr != max_cr:
                    logging.warning("Creature is of CR %s instead of %s" % (cr, cr_message))
                return creature

    def get_amulet(self):
        allowed_crs = ['0', '0.125', '0.25', '0.5', '1', '2', '3', '4']
        weightings = [0.05, 0.05, 0.05, 0.1, 0.25, 0.25, 0.15, 0.1]
        amulet_max_cr = random.choices(population=allowed_crs, weights=weightings, k=1)[0]
        return "Amulet CR: %s\n\tCreature: %s", amulet_max_cr, self.get_random_creature_of_cr(amulet_max_cr)

    def get_mundane(self):
        is_weapon = random.randint(1, 100) > 66
        options: LootOptions = self.weapons if is_weapon else self.armours
        return "%s", random.choice(options)

    def get_ring(self):
        return "%s", random.choice(self.rings)

    def get_prayer_stone(self):
        stone_options = set(map(lambda prayer_path: prayer_path["value"], self.prayer_paths))
        return "%s", random.choice(tuple(stone_options))

    def get_consumable(self):
        return "%s", LootController._get_multiple_items(random.choice(self.consumables),
                                                        lambda: random.randint(1, 4))

    def get_valid_enchants_for_weapon(self, weapon: LootOption) -> LootOptions:
        return list(filter(lambda enchant:
                           (not enchant.get("metadata")
                            or "weapon" in enchant["metadata"]
                            or ("Shield" == weapon["category"] and "amour" in enchant["metadata"]))
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

    def get_crafting_item(self):
        return "%s", LootController._get_multiple_items(random.choice(self.crafting_items),
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
        return "Enchants: %s", LootController._get_enchants_totalling(valid_enchants, n)

    def get_negatively_enchanted_item(self, n: int):
        base_type, valid_enchants = self.get_item_and_enchant_list()
        return "Base: %s\nEnchants:%s", base_type,\
               LootController._get_negative_enchants_totalling(valid_enchants, n)

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
        for metadata_tag in item["metadata"]:
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
        return {"amount": amount, "item": item}

    @staticmethod
    def _get_enchants_totalling(valid_enchants: LootOptions, total: int):
        current_total = 0
        enchants = []
        while total > current_total:
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
    def _load_challenge_ratings(do_flush) -> Dict[str, List[str]]:
        file_contents = LootController._load_raw_file_contents("monster", do_flush)
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
    def _create_relics(do_flush) -> LootOptions:
        relics = LootController._load_with_defaults("relic", do_flush, {
            "enabled": True,
            "found": False,
            "level": 1
        })
        return list(map(LootController._get_relic_with_defaults, relics))

    @staticmethod
    def _get_relic_with_defaults(relic: LootOption) -> LootOption:
        relic["available"] = list(map(LootController._get_default_relic_mod, relic["available"]))
        relic["existing"] = list(map(LootController._get_default_relic_mod, relic["existing"]))
        return relic

    @staticmethod
    def _get_default_relic_mod(relic_mod: LootOption) -> LootOption:
        mod_defaults = {
            "level": 1,
            "upgradeable": True,
            "points": 10,
        }
        relic_mod = {**mod_defaults, **relic_mod}
        relic_mod["level_up_points"] = relic_mod.get("level_up_points", relic_mod["points"])
        if "enabled" in relic_mod:
            relic_mod.pop("enabled")
        return relic_mod

    @staticmethod
    def _load_with_defaults(filename: str, do_flush: bool, defaults: LootOption) -> LootOptions:
        dicts = LootController._load_file_contents(filename, do_flush)
        return list(map(lambda option: {**defaults, **option}, dicts))

    @staticmethod
    def _load_file_contents(filename: str, do_flush: bool):
        file_contents = LootController._load_raw_file_contents(filename, do_flush)
        return json.loads(file_contents)

    @staticmethod
    def _write_file(filename: str, values):
        with open(DATA_DIR + filename + ".json", 'w') as f:
            json.dump(values, f, ensure_ascii=False, indent=2, sort_keys=True)
        logging.info("Updated %s" % filename)

    @staticmethod
    def _load_raw_file_contents(name, do_flush):
        with open(DATA_DIR + name + ".json") as data_file:
            if do_flush:
                data_file.flush()
                return LootController._load_raw_file_contents(name, False)
            return data_file.read()

    @staticmethod
    def _take_input(prompt: str, options: Collection[str], strict=True) -> Optional[str]:
        options: Set[str] = options if isinstance(options, set) else set(options)
        logging.info("Input Options: %s" % options)
        readline.set_completer(Completer(options).complete)
        choice: str = input("\n%s " % prompt)
        readline.set_completer(lambda text, state: None)
        if strict and choice not in options:
            logging.warning("%s is not a valid option (%s)" % (choice, options))
            return None
        return choice

    @staticmethod
    def _take_input_from_index(prompt: str, option_mappings: Collection[Any]) -> Optional[Tuple[int, Any]]:
        option_mappings: Dict[str, Any] = {str(i): option for i, option in enumerate(option_mappings)}
        logging.info("Input Options: %s" %
                     str(reduce(lambda option, next_option: "\n%s\n\n%s" % (option, next_option),  # TODO: prettyprint?
                                list(option_mappings.items()))))
        readline.set_completer(Completer(option_mappings.keys()).complete)
        choice: str = input("\n%s " % prompt)
        readline.set_completer(lambda text, state: None)
        if choice not in option_mappings:
            logging.warning("%s is not a valid option (%s)" % (choice, option_mappings))
            return None
        return int(choice), option_mappings.get(choice)


def get_int_from_str(string, default_integer=None):
    try:
        return int(string)
    except ValueError:
        logging.warning("Could not build an int out of '%s', using %d instead", string, default_integer)
        return default_integer


def do_continuously(f, prompt):
    while True:
        entered = input("\n%s " % prompt)
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


# TODO: move to LootController __init__
def define_action_map(mapped_loot_controller) -> Dict[int, Callable[[], Optional[Any]]]:
    return {
        loot_types.LootType.negatively_enchanted.value:
            lambda: mapped_loot_controller.get_negatively_enchanted_item(-25),
        loot_types.LootType.mundane.value: mapped_loot_controller.get_mundane,
        loot_types.LootType.consumable.value: mapped_loot_controller.get_consumable,
        loot_types.LootType.low_gold.value: lambda: str(random.randint(40, 50)) + " gold",
        loot_types.LootType.ring.value: lambda: "Ring: " + mapped_loot_controller.get_ring(),
        loot_types.LootType.low_enchant_item.value: lambda: mapped_loot_controller.get_enchanted_item_totalling(10),
        loot_types.LootType.amulet.value: mapped_loot_controller.get_amulet,
        loot_types.LootType.medium_enchant_item.value: lambda: mapped_loot_controller.get_enchanted_item_totalling(20),
        loot_types.LootType.high_enchant_item.value: lambda: mapped_loot_controller.get_enchanted_item_totalling(30),
        loot_types.LootType.crafting_item.value: mapped_loot_controller.get_crafting_item,
        loot_types.LootType.prayer_stone.value: mapped_loot_controller.get_prayer_stone,
        loot_types.LootType.relic.value: mapped_loot_controller.get_new_relic,
        24: lambda: do_continuously(mapped_loot_controller.get_random_creature_of_cr, "Monster CR:"),
        25: mapped_loot_controller.level_up_relic_by_choice,
        26: mapped_loot_controller.level_up_prayer_path,
    }


def _format_json(o) -> str:
    return json.dumps(o, sort_keys=True, indent=4)


def log_formatted_args(t):
    if type(t) == str:
        logging.info(t)
        return
    args = list(t)[1:]
    logging.info(t[0], *map(_format_json, args))


def generate_loot():
    loot_controller = LootController()
    loot_action_map = define_action_map(loot_controller)
    print_options()
    while True:
        readline.set_completer_delims(' \t\n;')
        readline.parse_and_bind("tab: complete")
        roll = get_int_from_str(input("\nLoot roll: "), 0)
        if roll == 0:
            roll = random.randint(1, 20)
            logging.info("Random roll %s (%s)", roll, loot_action_map[roll])
        elif roll < 0:
            exit(0)
        output = loot_action_map[roll]()
        if output:
            log_formatted_args(output)

        if roll > max(loot_action_map.keys()):
            print_options()


if __name__ == "__main__":
    logging.basicConfig(stream=sys.stdout, level=logging.INFO,
                        format='[%(levelname)s] %(asctime)-15s %(message)s',
                        datefmt='%H:%M:%S')
    generate_loot()
