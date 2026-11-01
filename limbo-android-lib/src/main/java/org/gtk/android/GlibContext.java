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
 * HARD CONTRACT: the class name "org/gtk/android/GlibContext" and its native
 * method runOnMain(Runnable) are resolved by name from
 * gdk/android/gdkandroidinit.c (FindClass + RegisterNatives). Renaming the
 * class or changing the parameter type breaks libgtk-4.so's android
 * backend initialisation.
 *
 * The only edits against upstream: the lambdas became anonymous classes
 * (same Java 8 source level, matching this code base's style), and the
 * androidx @UiThread annotation was dropped so the glue has no extra
 * dependency.
 */

package org.gtk.android;

import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.METHOD;

import java.lang.annotation.Target;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public final class GlibContext {
	@Target({METHOD, CONSTRUCTOR})
	public @interface GtkThread {}

	private GlibContext() {}

	public static native void runOnMain(Runnable runnable);

	public static <T> T blockForMain(final Supplier<T> runnable) {
		CountDownLatch barrier = new CountDownLatch(1);
		AtomicReference<T> retval = new AtomicReference<>();
		runOnMain(new Runnable() {
			@Override
			public void run() {
				retval.set(runnable.get());
				barrier.countDown();
			}
		});
		try {
			barrier.await();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException(e);
		}
		return retval.get();
	}

	public static void blockForMain(final Runnable runnable) {
		blockForMain(new Supplier<Void>() {
			@Override
			public Void get() {
				runnable.run();
				return null;
			}
		});
	}
}
