/*
Copyright (C) Max Kastanas 2012

 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 */
package com.max2idea.android.limbo.keyboard;

import android.view.KeyEvent;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Single source of truth for the physical key mapping used by both the built-in
 * VNC viewer and the SDL front-end.
 *
 * <p>Three different naming schemes meet in this class:
 * <ul>
 *   <li><b>RFB/X11 keysyms</b> - what a VNC server expects in a KeyEvent message.
 *       Latin-1 characters are their own keysym, the rest live in the
 *       0xFFxx "function key" block.</li>
 *   <li><b>Android {@link KeyEvent#KEYCODE_*} constants</b> - what the Android
 *       framework reports for a physical key.</li>
 *   <li><b>SDL scancodes</b> - what SDL 2.0.8 uses internally
 *       (see SDL_scancode.h, USB usage page 0x07).</li>
 * </ul>
 *
 * <p>Only keysyms whose value is unambiguous are listed. Anything that cannot be
 * pinned down with certainty is deliberately left out rather than guessed.
 */
public final class KeyMapping {

    /* ------------------------------------------------------------------ */
    /* RFB / X11 keysyms                                                  */
    /* ------------------------------------------------------------------ */

    public static final int KEYSYM_ESCAPE = 0xFF1B;
    public static final int KEYSYM_TAB = 0xFF09;
    public static final int KEYSYM_RETURN = 0xFF0D;
    public static final int KEYSYM_BACKSPACE = 0xFF08;
    public static final int KEYSYM_DELETE = 0xFFFF;
    public static final int KEYSYM_HOME = 0xFF50;
    public static final int KEYSYM_END = 0xFF57;
    public static final int KEYSYM_PAGE_UP = 0xFF55;
    public static final int KEYSYM_PAGE_DOWN = 0xFF56;
    public static final int KEYSYM_INSERT = 0xFF63;
    public static final int KEYSYM_UP = 0xFF52;
    public static final int KEYSYM_DOWN = 0xFF54;
    public static final int KEYSYM_LEFT = 0xFF51;
    public static final int KEYSYM_RIGHT = 0xFF53;
    public static final int KEYSYM_SHIFT_L = 0xFFE1;
    public static final int KEYSYM_SHIFT_R = 0xFFE2;
    public static final int KEYSYM_CONTROL_L = 0xFFE3;
    public static final int KEYSYM_CONTROL_R = 0xFFE4;
    public static final int KEYSYM_CAPS_LOCK = 0xFFE5;
    public static final int KEYSYM_ALT_L = 0xFFE9;
    public static final int KEYSYM_ALT_R = 0xFFEA;
    public static final int KEYSYM_SUPER_L = 0xFFEB;
    public static final int KEYSYM_SUPER_R = 0xFFEC;
    public static final int KEYSYM_NUM_LOCK = 0xFF7F;
    public static final int KEYSYM_SCROLL_LOCK = 0xFF14;
    public static final int KEYSYM_PRINT = 0xFF61;
    public static final int KEYSYM_PAUSE = 0xFF13;

    /** F1 .. F12, contiguous in the 0xFFBE..0xFFC9 block. */
    public static final int KEYSYM_F1 = 0xFFBE;

    /** Keypad digits 0..9 are 0xFFB0..0xFFB9. */
    public static final int KEYSYM_KP_0 = 0xFFB0;

    /* Keypad operators. */
    public static final int KEYSYM_KP_MULTIPLY = 0xFFAA;
    public static final int KEYSYM_KP_ADD = 0xFFAB;
    public static final int KEYSYM_KP_SEPARATOR = 0xFFAC;
    public static final int KEYSYM_KP_SUBTRACT = 0xFFAD;
    public static final int KEYSYM_KP_DECIMAL = 0xFFAE;
    public static final int KEYSYM_KP_DIVIDE = 0xFFAF;
    public static final int KEYSYM_KP_EQUALS = 0xFFBD;
    public static final int KEYSYM_KP_ENTER = 0xFF8D;

    /* ------------------------------------------------------------------ */
    /* SDL scancodes (SDL 2.0.8, SDL_scancode.h)                          */
    /* ------------------------------------------------------------------ */

    public static final int SCANCODE_A = 4;
    public static final int SCANCODE_Z = 29;
    public static final int SCANCODE_1 = 30;
    public static final int SCANCODE_0 = 39;
    public static final int SCANCODE_RETURN = 40;
    public static final int SCANCODE_ESCAPE = 41;
    public static final int SCANCODE_BACKSPACE = 42;
    public static final int SCANCODE_TAB = 43;
    public static final int SCANCODE_SPACE = 44;
    public static final int SCANCODE_MINUS = 45;
    public static final int SCANCODE_EQUALS = 46;
    public static final int SCANCODE_LEFTBRACKET = 47;
    public static final int SCANCODE_RIGHTBRACKET = 48;
    public static final int SCANCODE_BACKSLASH = 49;
    public static final int SCANCODE_SEMICOLON = 51;
    public static final int SCANCODE_APOSTROPHE = 52;
    public static final int SCANCODE_GRAVE = 53;
    public static final int SCANCODE_COMMA = 54;
    public static final int SCANCODE_PERIOD = 55;
    public static final int SCANCODE_SLASH = 56;
    public static final int SCANCODE_CAPSLOCK = 57;
    public static final int SCANCODE_F1 = 58;
    public static final int SCANCODE_F12 = 69;
    public static final int SCANCODE_PRINTSCREEN = 70;
    public static final int SCANCODE_SCROLLLOCK = 71;
    public static final int SCANCODE_PAUSE = 72;
    public static final int SCANCODE_INSERT = 73;
    public static final int SCANCODE_HOME = 74;
    public static final int SCANCODE_PAGEUP = 75;
    public static final int SCANCODE_DELETE = 76;
    public static final int SCANCODE_END = 77;
    public static final int SCANCODE_PAGEDOWN = 78;
    public static final int SCANCODE_RIGHT = 79;
    public static final int SCANCODE_LEFT = 80;
    public static final int SCANCODE_DOWN = 81;
    public static final int SCANCODE_UP = 82;
    public static final int SCANCODE_NUMLOCKCLEAR = 83;
    public static final int SCANCODE_KP_DIVIDE = 84;
    public static final int SCANCODE_KP_MULTIPLY = 85;
    public static final int SCANCODE_KP_MINUS = 86;
    public static final int SCANCODE_KP_PLUS = 87;
    public static final int SCANCODE_KP_ENTER = 88;
    public static final int SCANCODE_KP_1 = 89;
    public static final int SCANCODE_KP_9 = 97;
    public static final int SCANCODE_KP_0 = 98;
    public static final int SCANCODE_KP_PERIOD = 99;
    public static final int SCANCODE_KP_COMMA = 133;
    public static final int SCANCODE_KP_EQUALS = 103;
    public static final int SCANCODE_LCTRL = 224;
    public static final int SCANCODE_LSHIFT = 225;
    public static final int SCANCODE_LALT = 226;
    public static final int SCANCODE_LGUI = 227;
    public static final int SCANCODE_RCTRL = 228;
    public static final int SCANCODE_RSHIFT = 229;
    public static final int SCANCODE_RALT = 230;
    public static final int SCANCODE_RGUI = 231;

    /* ------------------------------------------------------------------ */
    /* Tables                                                              */
    /* ------------------------------------------------------------------ */

    /** One physical key, described in all three naming schemes. */
    public static final class Key {
        public final int androidKeyCode;
        public final int keysym;
        public final int scancode;
        public final String label;

        Key(int androidKeyCode, int keysym, int scancode, String label) {
            this.androidKeyCode = androidKeyCode;
            this.keysym = keysym;
            this.scancode = scancode;
            this.label = label;
        }
    }

    /**
     * Android keycode -> keysym. Built from {@link #KEYS} so the two views can
     * never drift apart.
     */
    private static final Map<Integer, Integer> ANDROID_TO_KEYSYM;

    /**
     * Android keycode -> SDL scancode. Built from {@link #KEYS}.
     */
    private static final Map<Integer, Integer> ANDROID_TO_SCANCODE;

    /**
     * Android key codes that carry a *shifted* symbol (@, +, *, # ...) and that
     * SDL's Android keymap does not know about. Each entry says which key has
     * to be pressed together with Shift to produce the wanted character.
     *
     * <p>The pairs follow the US layout:
     * {@code @ = Shift+2}, {@code + = Shift+=}, {@code * = Shift+8},
     * {@code # = Shift+3}.
     */
    private static final Map<Integer, Integer> SHIFTED_SYMBOLS;

    /** The shared table. */
    private static final Map<Integer, Key> BY_ANDROID_CODE;

    private static final Map<Integer, Key> KEYS;

    static {
        Map<Integer, Key> keys = new HashMap<>();

        // --- Editing / navigation -------------------------------------
        put(keys, KeyEvent.KEYCODE_ESCAPE, KEYSYM_ESCAPE, SCANCODE_ESCAPE, "Esc");
        put(keys, KeyEvent.KEYCODE_TAB, KEYSYM_TAB, SCANCODE_TAB, "Tab");
        put(keys, KeyEvent.KEYCODE_ENTER, KEYSYM_RETURN, SCANCODE_RETURN, "Enter");
        put(keys, KeyEvent.KEYCODE_DEL, KEYSYM_BACKSPACE, SCANCODE_BACKSPACE, "Backspace");
        put(keys, KeyEvent.KEYCODE_FORWARD_DEL, KEYSYM_DELETE, SCANCODE_DELETE, "Delete");
        put(keys, KeyEvent.KEYCODE_INSERT, KEYSYM_INSERT, SCANCODE_INSERT, "Insert");
        put(keys, KeyEvent.KEYCODE_MOVE_HOME, KEYSYM_HOME, SCANCODE_HOME, "Home");
        put(keys, KeyEvent.KEYCODE_MOVE_END, KEYSYM_END, SCANCODE_END, "End");
        put(keys, KeyEvent.KEYCODE_PAGE_UP, KEYSYM_PAGE_UP, SCANCODE_PAGEUP, "PgUp");
        put(keys, KeyEvent.KEYCODE_PAGE_DOWN, KEYSYM_PAGE_DOWN, SCANCODE_PAGEDOWN, "PgDn");
        put(keys, KeyEvent.KEYCODE_DPAD_UP, KEYSYM_UP, SCANCODE_UP, "Up");
        put(keys, KeyEvent.KEYCODE_DPAD_DOWN, KEYSYM_DOWN, SCANCODE_DOWN, "Down");
        put(keys, KeyEvent.KEYCODE_DPAD_LEFT, KEYSYM_LEFT, SCANCODE_LEFT, "Left");
        put(keys, KeyEvent.KEYCODE_DPAD_RIGHT, KEYSYM_RIGHT, SCANCODE_RIGHT, "Right");

        // --- Function keys --------------------------------------------
        for (int i = 0; i < 12; i++) {
            put(keys, KeyEvent.KEYCODE_F1 + i, KEYSYM_F1 + i, SCANCODE_F1 + i, "F" + (i + 1));
        }

        // --- Modifiers -----------------------------------------------
        put(keys, KeyEvent.KEYCODE_CTRL_LEFT, KEYSYM_CONTROL_L, SCANCODE_LCTRL, "LCtrl");
        put(keys, KeyEvent.KEYCODE_CTRL_RIGHT, KEYSYM_CONTROL_R, SCANCODE_RCTRL, "RCtrl");
        put(keys, KeyEvent.KEYCODE_SHIFT_LEFT, KEYSYM_SHIFT_L, SCANCODE_LSHIFT, "LShift");
        put(keys, KeyEvent.KEYCODE_SHIFT_RIGHT, KEYSYM_SHIFT_R, SCANCODE_RSHIFT, "RShift");
        put(keys, KeyEvent.KEYCODE_ALT_LEFT, KEYSYM_ALT_L, SCANCODE_LALT, "LAlt");
        put(keys, KeyEvent.KEYCODE_ALT_RIGHT, KEYSYM_ALT_R, SCANCODE_RALT, "RAlt");
        put(keys, KeyEvent.KEYCODE_META_LEFT, KEYSYM_SUPER_L, SCANCODE_LGUI, "LMeta");
        put(keys, KeyEvent.KEYCODE_META_RIGHT, KEYSYM_SUPER_R, SCANCODE_RGUI, "RMeta");
        put(keys, KeyEvent.KEYCODE_CAPS_LOCK, KEYSYM_CAPS_LOCK, SCANCODE_CAPSLOCK, "CapsLock");
        put(keys, KeyEvent.KEYCODE_NUM_LOCK, KEYSYM_NUM_LOCK, SCANCODE_NUMLOCKCLEAR, "NumLock");
        put(keys, KeyEvent.KEYCODE_SCROLL_LOCK, KEYSYM_SCROLL_LOCK, SCANCODE_SCROLLLOCK, "ScrollLock");

        // --- System ---------------------------------------------------
        put(keys, KeyEvent.KEYCODE_SYSRQ, KEYSYM_PRINT, SCANCODE_PRINTSCREEN, "PrintScreen");
        put(keys, KeyEvent.KEYCODE_BREAK, KEYSYM_PAUSE, SCANCODE_PAUSE, "Pause");

        // --- Keypad ---------------------------------------------------
        // SDL scancodes run KP_1 (89) .. KP_9 (97) and put KP_0 (98) last, so
        // the digit run is not contiguous the way the keysym run is.
        final int[] keypadScancodes = {
                SCANCODE_KP_0,                                          // KP0
                SCANCODE_KP_1, SCANCODE_KP_1 + 1, SCANCODE_KP_1 + 2, SCANCODE_KP_1 + 3,
                SCANCODE_KP_1 + 4, SCANCODE_KP_1 + 5, SCANCODE_KP_1 + 6, SCANCODE_KP_1 + 7,
                SCANCODE_KP_1 + 8,                                      // KP9
        };
        for (int i = 0; i <= 9; i++) {
            put(keys, KeyEvent.KEYCODE_NUMPAD_0 + i, KEYSYM_KP_0 + i, keypadScancodes[i], "KP" + i);
        }
        put(keys, KeyEvent.KEYCODE_NUMPAD_MULTIPLY, KEYSYM_KP_MULTIPLY, SCANCODE_KP_MULTIPLY, "KP*");
        put(keys, KeyEvent.KEYCODE_NUMPAD_ADD, KEYSYM_KP_ADD, SCANCODE_KP_PLUS, "KP+");
        put(keys, KeyEvent.KEYCODE_NUMPAD_SUBTRACT, KEYSYM_KP_SUBTRACT, SCANCODE_KP_MINUS, "KP-");
        put(keys, KeyEvent.KEYCODE_NUMPAD_DIVIDE, KEYSYM_KP_DIVIDE, SCANCODE_KP_DIVIDE, "KP/");
        put(keys, KeyEvent.KEYCODE_NUMPAD_DOT, KEYSYM_KP_DECIMAL, SCANCODE_KP_PERIOD, "KP.");
        put(keys, KeyEvent.KEYCODE_NUMPAD_COMMA, KEYSYM_KP_SEPARATOR, SCANCODE_KP_COMMA, "KP,");
        put(keys, KeyEvent.KEYCODE_NUMPAD_EQUALS, KEYSYM_KP_EQUALS, SCANCODE_KP_EQUALS, "KP=");
        put(keys, KeyEvent.KEYCODE_NUMPAD_ENTER, KEYSYM_KP_ENTER, SCANCODE_KP_ENTER, "KPEnter");
        // The keypad parenthesis keys (KEYCODE_NUMPAD_LEFT_PAREN /
        // KEYCODE_NUMPAD_RIGHT_PAREN) are left out on purpose: X11 has no
        // keysym for them, so there is nothing certain to send.

        // --- Letters and digits ---------------------------------------
        // The keysym of an ASCII letter/digit is its own code point.
        for (int i = 0; i < 26; i++) {
            int code = KeyEvent.KEYCODE_A + i;
            put(keys, code, 'a' + i, SCANCODE_A + i, String.valueOf((char) ('A' + i)));
        }
        // Android's KEYCODE_0..KEYCODE_9 are already in numeric order, but SDL
        // scancodes run 1..9 (30..38) and put 0 (39) last.
        final int[] digitScancodes = {
                SCANCODE_0,                                          // 0
                SCANCODE_1, SCANCODE_1 + 1, SCANCODE_1 + 2, SCANCODE_1 + 3,
                SCANCODE_1 + 4, SCANCODE_1 + 5, SCANCODE_1 + 6, SCANCODE_1 + 7,
                SCANCODE_1 + 8,                                      // 9
        };
        for (int digit = 0; digit <= 9; digit++) {
            put(keys, KeyEvent.KEYCODE_0 + digit, '0' + digit, digitScancodes[digit],
                    String.valueOf(digit));
        }

        // --- Punctuation -----------------------------------------------
        put(keys, KeyEvent.KEYCODE_GRAVE, '`', SCANCODE_GRAVE, "`");
        put(keys, KeyEvent.KEYCODE_MINUS, '-', SCANCODE_MINUS, "-");
        put(keys, KeyEvent.KEYCODE_EQUALS, '=', SCANCODE_EQUALS, "=");
        put(keys, KeyEvent.KEYCODE_LEFT_BRACKET, '[', SCANCODE_LEFTBRACKET, "[");
        put(keys, KeyEvent.KEYCODE_RIGHT_BRACKET, ']', SCANCODE_RIGHTBRACKET, "]");
        put(keys, KeyEvent.KEYCODE_BACKSLASH, '\\', SCANCODE_BACKSLASH, "\\");
        put(keys, KeyEvent.KEYCODE_SEMICOLON, ';', SCANCODE_SEMICOLON, ";");
        put(keys, KeyEvent.KEYCODE_APOSTROPHE, '\'', SCANCODE_APOSTROPHE, "'");
        put(keys, KeyEvent.KEYCODE_COMMA, ',', SCANCODE_COMMA, ",");
        put(keys, KeyEvent.KEYCODE_PERIOD, '.', SCANCODE_PERIOD, ".");
        put(keys, KeyEvent.KEYCODE_SLASH, '/', SCANCODE_SLASH, "/");
        put(keys, KeyEvent.KEYCODE_SPACE, ' ', SCANCODE_SPACE, "Space");

        KEYS = Collections.unmodifiableMap(keys);

        Map<Integer, Integer> toKeysym = new HashMap<>();
        Map<Integer, Integer> toScancode = new HashMap<>();
        Map<Integer, Key> byCode = new HashMap<>();
        for (Key key : keys.values()) {
            toKeysym.put(key.androidKeyCode, key.keysym);
            toScancode.put(key.androidKeyCode, key.scancode);
            byCode.put(key.androidKeyCode, key);
        }
        ANDROID_TO_KEYSYM = Collections.unmodifiableMap(toKeysym);
        ANDROID_TO_SCANCODE = Collections.unmodifiableMap(toScancode);
        BY_ANDROID_CODE = Collections.unmodifiableMap(byCode);

        // Keys whose Android keycode stands for a shifted symbol. SDL's Android
        // keymap has no entry for them, so we re-send them as Shift + base key.
        // The base keys are given as Android keycodes.
        Map<Integer, Integer> shifted = new HashMap<>();
        shifted.put(KeyEvent.KEYCODE_AT, KeyEvent.KEYCODE_2);       // @ = Shift+2
        shifted.put(KeyEvent.KEYCODE_PLUS, KeyEvent.KEYCODE_EQUALS); // + = Shift+=
        shifted.put(KeyEvent.KEYCODE_STAR, KeyEvent.KEYCODE_8);      // * = Shift+8
        shifted.put(KeyEvent.KEYCODE_POUND, KeyEvent.KEYCODE_3);     // # = Shift+3
        SHIFTED_SYMBOLS = Collections.unmodifiableMap(shifted);
    }

    private KeyMapping() {
    }

    private static void put(Map<Integer, Key> keys, int androidKeyCode, int keysym,
                            int scancode, String label) {
        keys.put(androidKeyCode, new Key(androidKeyCode, keysym, scancode, label));
    }

    /* ------------------------------------------------------------------ */
    /* Public API                                                          */
    /* ------------------------------------------------------------------ */

    /**
     * @return the RFB keysym for an Android key code, or 0 when the key has no
     *         known keysym (in which case the caller should not send it).
     */
    public static int keysymFor(int androidKeyCode) {
        Integer keysym = ANDROID_TO_KEYSYM.get(androidKeyCode);
        return keysym == null ? 0 : keysym;
    }

    /**
     * @return the SDL scancode for an Android key code, or 0 when unknown.
     */
    public static int scancodeFor(int androidKeyCode) {
        Integer scancode = ANDROID_TO_SCANCODE.get(androidKeyCode);
        return scancode == null ? 0 : scancode;
    }

    /** @return the shared entry for an Android key code, or null. */
    public static Key forAndroidKeyCode(int androidKeyCode) {
        return BY_ANDROID_CODE.get(androidKeyCode);
    }

    /** @return an immutable view of the whole shared table. */
    public static Map<Integer, Key> all() {
        return KEYS;
    }

    /**
     * Some keys are reported by Android with a keycode that stands for the
     * shifted symbol (KEYCODE_AT, KEYCODE_PLUS, KEYCODE_STAR, KEYCODE_POUND).
     * SDL's Android keymap does not know those, so they have to be re-sent as
     * Shift plus the unshifted key.
     *
     * @param androidKeyCode the Android keycode of the pressed key
     * @return the Android keycode of the key that has to be pressed together
     *         with Shift, or 0 when the key needs no such workaround.
     */
    public static int shiftedSymbolBaseKeyCode(int androidKeyCode) {
        Integer base = SHIFTED_SYMBOLS.get(androidKeyCode);
        return base == null ? 0 : base;
    }

    /**
     * Converts a character to the keysym a VNC server expects.
     *
     * @return the keysym, or 0 when the character cannot be represented.
     */
    public static int keysymForChar(char c) {
        if (c == '\n' || c == '\r') {
            return KEYSYM_RETURN;
        }
        if (c == '\b') {
            return KEYSYM_BACKSPACE;
        }
        if (c == '\t') {
            return KEYSYM_TAB;
        }
        if (c >= 0x20 && c <= 0x7E) {
            return c;
        }
        if (c >= 0xA0 && c <= 0xFF) {
            return c;
        }
        // Unicode keysyms live in the 0x01000000 block.
        if (c < 0x10000) {
            return 0x01000000 | c;
        }
        return 0;
    }
}
