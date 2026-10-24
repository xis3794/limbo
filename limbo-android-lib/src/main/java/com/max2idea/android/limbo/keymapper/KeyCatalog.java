package com.max2idea.android.limbo.keymapper;

import android.view.KeyEvent;

import com.limbo.emu.lib.R;
import com.max2idea.android.limbo.main.Config;

import java.util.ArrayList;
import java.util.List;

/**
 * Full physical-keyboard catalogue used by the key mapper editor. Groups the keys
 * that can be found on a normal PC keyboard (letters, digits, F-keys, editing and
 * navigation, modifiers, punctuation, keypad, system/media and mouse buttons).
 */
public final class KeyCatalog {

    public static final class Key {
        public final String label;
        public final int code;
        public final boolean mouse;

        Key(String label, int code) {
            this(label, code, false);
        }

        Key(String label, int code, boolean mouse) {
            this.label = label;
            this.code = code;
            this.mouse = mouse;
        }
    }

    public static final class Category {
        public final int titleRes;
        public final List<Key> keys;

        Category(int titleRes, List<Key> keys) {
            this.titleRes = titleRes;
            this.keys = keys;
        }
    }

    private KeyCatalog() {
    }

    public static List<Category> categories() {
        List<Category> categories = new ArrayList<>();

        List<Key> letters = new ArrayList<>();
        for (int i = 0; i < 26; i++) {
            letters.add(new Key(String.valueOf((char) ('A' + i)), KeyEvent.KEYCODE_A + i));
        }
        categories.add(new Category(R.string.keycat_letters, letters));

        List<Key> digits = new ArrayList<>();
        for (int i = 0; i <= 9; i++) {
            digits.add(new Key(String.valueOf(i), KeyEvent.KEYCODE_0 + i));
        }
        categories.add(new Category(R.string.keycat_digits, digits));

        List<Key> function = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            function.add(new Key("F" + (i + 1), KeyEvent.KEYCODE_F1 + i));
        }
        categories.add(new Category(R.string.keycat_function, function));

        List<Key> editing = new ArrayList<>();
        editing.add(new Key("↑", KeyEvent.KEYCODE_DPAD_UP));
        editing.add(new Key("↓", KeyEvent.KEYCODE_DPAD_DOWN));
        editing.add(new Key("←", KeyEvent.KEYCODE_DPAD_LEFT));
        editing.add(new Key("→", KeyEvent.KEYCODE_DPAD_RIGHT));
        editing.add(new Key("Home", KeyEvent.KEYCODE_MOVE_HOME));
        editing.add(new Key("End", KeyEvent.KEYCODE_MOVE_END));
        editing.add(new Key("PgUp", KeyEvent.KEYCODE_PAGE_UP));
        editing.add(new Key("PgDn", KeyEvent.KEYCODE_PAGE_DOWN));
        editing.add(new Key("Ins", KeyEvent.KEYCODE_INSERT));
        editing.add(new Key("Bksp", KeyEvent.KEYCODE_DEL));
        editing.add(new Key("Del", KeyEvent.KEYCODE_FORWARD_DEL));
        editing.add(new Key("Esc", KeyEvent.KEYCODE_ESCAPE));
        editing.add(new Key("Tab", KeyEvent.KEYCODE_TAB));
        editing.add(new Key("Enter", KeyEvent.KEYCODE_ENTER));
        editing.add(new Key("Space", KeyEvent.KEYCODE_SPACE));
        categories.add(new Category(R.string.keycat_editing, editing));

        List<Key> modifiers = new ArrayList<>();
        modifiers.add(new Key("LCtrl", KeyEvent.KEYCODE_CTRL_LEFT));
        modifiers.add(new Key("RCtrl", KeyEvent.KEYCODE_CTRL_RIGHT));
        modifiers.add(new Key("LAlt", KeyEvent.KEYCODE_ALT_LEFT));
        modifiers.add(new Key("RAlt", KeyEvent.KEYCODE_ALT_RIGHT));
        modifiers.add(new Key("LShift", KeyEvent.KEYCODE_SHIFT_LEFT));
        modifiers.add(new Key("RShift", KeyEvent.KEYCODE_SHIFT_RIGHT));
        modifiers.add(new Key("LMeta", KeyEvent.KEYCODE_META_LEFT));
        modifiers.add(new Key("RMeta", KeyEvent.KEYCODE_META_RIGHT));
        modifiers.add(new Key("Fn", KeyEvent.KEYCODE_FUNCTION));
        modifiers.add(new Key("Caps", KeyEvent.KEYCODE_CAPS_LOCK));
        modifiers.add(new Key("NumL", KeyEvent.KEYCODE_NUM_LOCK));
        modifiers.add(new Key("Scrl", KeyEvent.KEYCODE_SCROLL_LOCK));
        categories.add(new Category(R.string.keycat_modifiers, modifiers));

        List<Key> symbols = new ArrayList<>();
        symbols.add(new Key("`~", KeyEvent.KEYCODE_GRAVE));
        symbols.add(new Key("- _", KeyEvent.KEYCODE_MINUS));
        symbols.add(new Key("= +", KeyEvent.KEYCODE_EQUALS));
        symbols.add(new Key("[ {", KeyEvent.KEYCODE_LEFT_BRACKET));
        symbols.add(new Key("] }", KeyEvent.KEYCODE_RIGHT_BRACKET));
        symbols.add(new Key("\\ |", KeyEvent.KEYCODE_BACKSLASH));
        symbols.add(new Key("; :", KeyEvent.KEYCODE_SEMICOLON));
        symbols.add(new Key("' \"", KeyEvent.KEYCODE_APOSTROPHE));
        symbols.add(new Key(", <", KeyEvent.KEYCODE_COMMA));
        symbols.add(new Key(". >", KeyEvent.KEYCODE_PERIOD));
        symbols.add(new Key("/ ?", KeyEvent.KEYCODE_SLASH));
        categories.add(new Category(R.string.keycat_symbols, symbols));

        List<Key> numpad = new ArrayList<>();
        for (int i = 0; i <= 9; i++) {
            numpad.add(new Key("N" + i, KeyEvent.KEYCODE_NUMPAD_0 + i));
        }
        numpad.add(new Key("N+", KeyEvent.KEYCODE_NUMPAD_ADD));
        numpad.add(new Key("N-", KeyEvent.KEYCODE_NUMPAD_SUBTRACT));
        numpad.add(new Key("N*", KeyEvent.KEYCODE_NUMPAD_MULTIPLY));
        numpad.add(new Key("N/", KeyEvent.KEYCODE_NUMPAD_DIVIDE));
        numpad.add(new Key("N.", KeyEvent.KEYCODE_NUMPAD_DOT));
        numpad.add(new Key("N,", KeyEvent.KEYCODE_NUMPAD_COMMA));
        numpad.add(new Key("N=", KeyEvent.KEYCODE_NUMPAD_EQUALS));
        numpad.add(new Key("N(", KeyEvent.KEYCODE_NUMPAD_LEFT_PAREN));
        numpad.add(new Key("N)", KeyEvent.KEYCODE_NUMPAD_RIGHT_PAREN));
        numpad.add(new Key("NEnt", KeyEvent.KEYCODE_NUMPAD_ENTER));
        categories.add(new Category(R.string.keycat_numpad, numpad));

        List<Key> system = new ArrayList<>();
        system.add(new Key("PrtSc", KeyEvent.KEYCODE_SYSRQ));
        system.add(new Key("Break", KeyEvent.KEYCODE_BREAK));
        system.add(new Key("Menu", KeyEvent.KEYCODE_MENU));
        system.add(new Key("Search", KeyEvent.KEYCODE_SEARCH));
        system.add(new Key("Vol+", KeyEvent.KEYCODE_VOLUME_UP));
        system.add(new Key("Vol-", KeyEvent.KEYCODE_VOLUME_DOWN));
        system.add(new Key("Mute", KeyEvent.KEYCODE_VOLUME_MUTE));
        system.add(new Key("Play", KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
        system.add(new Key("Next", KeyEvent.KEYCODE_MEDIA_NEXT));
        system.add(new Key("Prev", KeyEvent.KEYCODE_MEDIA_PREVIOUS));
        system.add(new Key("Sleep", KeyEvent.KEYCODE_SLEEP));
        system.add(new Key("Wake", KeyEvent.KEYCODE_WAKEUP));
        system.add(new Key("Power", KeyEvent.KEYCODE_POWER));
        categories.add(new Category(R.string.keycat_system, system));

        List<Key> mouse = new ArrayList<>();
        mouse.add(new Key("Mouse L", Config.SDL_MOUSE_LEFT, true));
        mouse.add(new Key("Mouse M", Config.SDL_MOUSE_MIDDLE, true));
        mouse.add(new Key("Mouse R", Config.SDL_MOUSE_RIGHT, true));
        categories.add(new Category(R.string.keycat_mouse, mouse));

        return categories;
    }
}
