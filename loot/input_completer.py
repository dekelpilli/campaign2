import readline


class Completer:
    def __init__(self, options_list):
        self.options = set(options_list)

    def complete(self, text, state):
        text = text.lower()
        for option in self.options:
            if option.lower().startswith(text):
                if not state:
                    return option
                else:
                    state -= 1


if __name__ == "__main__":
    comp = Completer(["test", "wow123", "wow456"])
    # we want to treat '/' as part of a word, so override the delimiters
    readline.set_completer_delims(' \t\n;')
    readline.parse_and_bind("tab: complete")
    readline.set_completer(comp.complete)
    input('Enter section name: ')
