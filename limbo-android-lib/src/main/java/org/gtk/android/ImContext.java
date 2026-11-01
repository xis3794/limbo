/*
 * Copyright (c) 2024 Florian "sp1rit" <sp1rit@disroot.org>
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library. If not, see <http://www.gnu.org/licenses/>.
 *
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */

/*
 * Ported from gtk4android (gdk/android/glue/java/org/gtk/android).
 *
 * HARD CONTRACT: GDK resolves "org/gtk/android/ImContext", its native methods
 * and the SurroundingRetVal field names/signatures by name. Keep them exact.
 *
 * Edits against upstream: lambdas became anonymous classes, the androidx
 * annotations were dropped, getSystemService(Class) was replaced by the
 * string overload (identical behaviour, no extra dependency), and the
 * java.util.logging chatter was replaced by android.util.Log.
 */

package org.gtk.android;

import android.text.Editable;
import android.text.Selection;
import android.util.Log;
import android.view.KeyEvent;
import android.content.Context;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.InputMethodManager;

public final class ImContext {
	private static final String TAG = "GtkImContext";

	public static final class SurroundingRetVal {
		public String text;
		public int cursor_index;
		public int anchor_index;

		private SurroundingRetVal(String text, int cursor_idx, int anchor_idx) {
			this.text = text;
			this.cursor_index = cursor_idx;
			this.anchor_index = anchor_idx;
		}
	}

	private long native_ptr;

	public ImContext(long native_ptr) {
		this.native_ptr = native_ptr;
	}

	public native int getInputType();

	public native SurroundingRetVal getSurrounding();
	public native boolean deleteSurrounding(int offset, int n_chars);

	public native String getPreedit();
	public native void updatePreedit(String preedit, int cursor);

	public native boolean commit(String string);

	/**
	 * Called by GDK (by name) to restart the input connection after the
	 * active IM context changed.
	 */
	private static void reset(View view) {
		InputMethodManager imm = (InputMethodManager)
				view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
		if (imm != null) {
			imm.restartInput(view);
		}
	}

	final class ImeConnection extends BaseInputConnection {

		public ImeConnection(View target) {
			super(target, true);
		}

		@Override
		public boolean setComposingText(CharSequence text, int newCursorPosition) {
			Log.d(TAG, "IME: setComposingText()");
			super.setComposingText(text, newCursorPosition);

			final Editable content = getEditable();
			if (content == null) {
				return true;
			}
			GlibContext.blockForMain(new Runnable() {
				@Override
				public void run() {
					int a = Selection.getSelectionStart(content);
					int b = Selection.getSelectionEnd(content);
					if (a > b)
						b = a;
					ImContext.this.updatePreedit(content.toString(), b);
				}
			});
			return true;
		}

		@Override
		public boolean finishComposingText() {
			Log.d(TAG, "IME: finishComposingText()");
			super.finishComposingText();

			final Editable content = getEditable();
			if (content == null) {
				return true;
			}
			if (content.length() > 0) {
				GlibContext.blockForMain(new Runnable() {
					@Override
					public void run() {
						ImContext.this.commit(content.toString());
					}
				});
			}
			content.clear();
			return true;
		}

		@Override
		public boolean commitText(CharSequence text, int newCursorPosition) {
			Log.d(TAG, "IME: commitText(\"" + text + "\", " + newCursorPosition + ")");
			super.commitText(text, newCursorPosition);

			final Editable content = getEditable();
			if (content == null) {
				return true;
			}
			GlibContext.blockForMain(new Runnable() {
				@Override
				public void run() {
					ImContext.this.commit(content.toString());
				}
			});
			content.clear();
			return true;
		}

		@Override
		public boolean deleteSurroundingText(int leftLength, int rightLength) {
			Log.d(TAG, "IME: deleteSurroundingText(" + leftLength + ", " + rightLength + ")");

			// The stock Samsung keyboard with "Auto check spelling" enabled
			// sends leftLength > 1, so one backspace per character.
			KeyEvent deleteKey = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL);
			for (int i = 0; i < leftLength; i++)
				sendKeyEvent(deleteKey);
			return super.deleteSurroundingText(leftLength, rightLength);
		}
	}
}
