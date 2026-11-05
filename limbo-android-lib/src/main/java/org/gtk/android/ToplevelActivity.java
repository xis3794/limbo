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
 * HARD CONTRACT: the class names, field names, method names and their JNI
 * signatures in this package are resolved by name from
 * gdk/android/gdkandroidinit.c (FindClass / GetFieldID / GetMethodID) and
 * RegisterNatives. Renaming anything, or changing a parameter type, makes
 * libgtk-4.so fail to initialise the android backend at runtime.
 *
 * The only edits against upstream are API-level ones. This project compiles
 * against SDK 29 while upstream uses symbols from API 30
 * (WindowInsets.Type, getInsetsController, setDecorFitsSystemWindows,
 * getCurrentWindowMetrics) and API 33 (OnBackInvokedCallback). Those code
 * paths were rewritten on top of the pre-30 APIs, guarded with reflection where
 * the newer behaviour is actually needed. GDK android itself only loads on
 * API 31+ (AMotionEvent/AKeyEvent are API 31 exports), so the guarded branches
 * are the ones that run on every device that can use GTK at all.
 */

package org.gtk.android;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Insets;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.DragEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.PointerIcon;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ToplevelActivity extends Activity {
	public final static String toplevelIdentifierKey = "gdk/toplevel-identifier";

	private static final String TAG = "ToplevelActivity";

	public static class UnregisteredSurfaceException extends Exception {
		public UnregisteredSurfaceException(Object surface) {
			super(String.format("Surface \"%s\" was not registered within GDK", surface.toString()));
		}
	}

	public static class GdkContext {
		@GlibContext.GtkThread
		private static native void _set_latest_activity(ToplevelActivity activity);

		@GlibContext.GtkThread
		public static native void activate();
		@GlibContext.GtkThread
		public static native void open(Uri uri, String action);
	}

	/**
	 * Shows/hides the IME through InputMethodManager instead of
	 * WindowInsetsController (API 30). The window never resizes under the
	 * keyboard, so this is the same observable behaviour on the API levels
	 * GDK android can run on.
	 */
	private void setImeVisibility(boolean visible, View target) {
		if (target == null) {
			return;
		}
		InputMethodManager imm =
				(InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
		if (imm == null) {
			return;
		}
		if (visible) {
			target.requestFocus();
			imm.showSoftInput(target, InputMethodManager.SHOW_IMPLICIT);
		} else {
			imm.hideSoftInputFromWindow(target.getWindowToken(), 0);
		}
	}

	public class ToplevelView extends ViewGroup {
		public class Surface extends SurfaceView implements SurfaceHolder.Callback {
			private long surfaceIdentifier = 0;

			private RectF[] inputRegion = null;

			private ImContext activeImContext = null;

			@GlibContext.GtkThread
			private native void bindNative(long identifier) throws UnregisteredSurfaceException;
			@GlibContext.GtkThread
			private native void notifyAttached();
			@GlibContext.GtkThread
			private native void notifyLayoutSurface(int width, int height, float scale);
			/**
			 * Tells GDK whether this surface is actually on screen. The Android
			 * backend keeps a per-surface visible flag and drops frames while it
			 * is false, so it has to follow the SurfaceHolder lifecycle.
			 */
			@GlibContext.GtkThread
			private native void notifyVisibility(boolean visible);
			@GlibContext.GtkThread
			private native void notifyLayoutPosition(int x, int y);
			@GlibContext.GtkThread
			private native void notifyDetached();

			@GlibContext.GtkThread
			private native void notifyDNDStartFailed(ClipboardProvider.NativeDragIdentifier identifier);

			@GlibContext.GtkThread
			private native void notifyKeyEvent(KeyEvent event);
			@GlibContext.GtkThread
			private native void notifyMotionEvent(int identifier, MotionEvent event);
			@GlibContext.GtkThread
			private native boolean notifyDragEvent(DragEvent event);

			@GlibContext.GtkThread
			public Surface(long identifier) {
				super(ToplevelActivity.this);
				setVisibility(GONE);

				try {
					bindNative(identifier);
					this.surfaceIdentifier = identifier;
				} catch (UnregisteredSurfaceException err) {
					Log.w(TAG, String.valueOf(err.getMessage()));
					drop();
				}

				setZOrderOnTop(true);
				getHolder().setFormat(PixelFormat.RGBA_8888);
				getHolder().addCallback(this);

				setFocusable(true);
				setFocusableInTouchMode(true);
			}

			/** 0 = nothing queued, 1 = show keyboard, -1 = hide keyboard. */
			private int queuedImeKeyboardState;

			@GlibContext.GtkThread
			public void setImeKeyboardState(boolean state) {
				boolean wasQueued = queuedImeKeyboardState != 0;
				queuedImeKeyboardState = state ? 1 : -1;
				if (wasQueued) {
					return;
				}
				GlibContext.runOnMain(new Runnable() {
					@Override
					public void run() {
						final boolean imeKeyboardState = queuedImeKeyboardState > 0;
						queuedImeKeyboardState = 0;
						runOnUiThread(new Runnable() {
							@Override
							public void run() {
								setImeVisibility(imeKeyboardState, Surface.this);
							}
						});
					}
				});
			}

			public void setVisibility(boolean visible) {
				final boolean show = visible;
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						Surface.super.setVisibility(show ? VISIBLE : GONE);
					}
				});
			}

			public void setInputRegion(final RectF[] region) {
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						inputRegion = region;
					}
				});
			}

			public void dropCursorIcon() {
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						setPointerIcon(null);
					}
				});
			}

			public void setCursorFromId(final int cursor) {
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						setPointerIcon(PointerIcon.getSystemIcon(getContext(), cursor));
					}
				});
			}

			public void setCursorFromBitmap(final Bitmap cursor, final float hotspotX, final float hotspotY) {
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						setPointerIcon(PointerIcon.create(cursor, hotspotX, hotspotY));
					}
				});
			}

			public void startDND(final ClipData clipdata, final View.DragShadowBuilder shadow,
								 final ClipboardProvider.NativeDragIdentifier identifier, final int flags) {
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						if (!startDragAndDrop(clipdata, shadow, identifier, flags)) {
							notifyDNDStartFailed(identifier);
						}
					}
				});
			}

			public void updateDND(final View.DragShadowBuilder shadow) {
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						updateDragShadow(shadow);
					}
				});
			}

			public void cancelDND() {
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						cancelDragAndDrop();
					}
				});
			}

			public void setActiveImContext(ImContext context) {
				if (activeImContext == context) {
					return;
				}
				activeImContext = context;
				InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
				if (imm != null) {
					imm.restartInput(this);
				}
			}

			public void reposition(final int x, final int y, final int width, final int height) {
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						setLayoutParams(new LayoutParams(x, y, width, height));
					}
				});
			}

			public void drop() {
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						ToplevelView.this.removeView(Surface.this);
					}
				});
			}

			private boolean motionEventProxy(MotionEvent event) {
				if (event == null) {
					return false;
				}

				if (this != ToplevelView.this.grabbed) {
					// Grabbed surfaces get every event regardless of the input
					// region; all others are filtered by it.
					if (this.inputRegion != null) {
						boolean inside = false;
						for (RectF region : this.inputRegion) {
							if (region.contains(event.getX(), event.getY())) {
								inside = true;
								break;
							}
						}
						if (!inside) {
							return false;
						}
					}
				}

				final int identity = System.identityHashCode(event);
				// MotionEvent.obtain() translates the event into the current
				// view's coordinate space, hence obtainNoHistory() here.
				final MotionEvent copy = MotionEvent.obtainNoHistory(event);
				GlibContext.runOnMain(new Runnable() {
					@Override
					public void run() {
						notifyMotionEvent(identity, copy);
					}
				});
				return true;
			}

			@Override
			public boolean onGenericMotionEvent(MotionEvent event) {
				return motionEventProxy(event);
			}

			@Override
			public boolean onHoverEvent(MotionEvent event) {
				return motionEventProxy(event);
			}

			@Override
			public boolean onTouchEvent(MotionEvent event) {
				return motionEventProxy(event);
			}

			private boolean keyEventProxy(final KeyEvent event) {
				if (event == null) {
					return false;
				}
				/*
				 * Once an IME (InputConnection) is attached, back presses start
				 * arriving through onKeyDown/-Up. Returning true there would
				 * stop onBackPressed() from running and the activity could
				 * never be closed, so let the back key through.
				 */
				if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
					return false;
				}
				GlibContext.runOnMain(new Runnable() {
					@Override
					public void run() {
						notifyKeyEvent(event);
					}
				});
				return true;
			}

			@Override
			public boolean onKeyDown(int keyCode, KeyEvent event) {
				return keyEventProxy(event);
			}

			@Override
			public boolean onKeyUp(int keyCode, KeyEvent event) {
				return keyEventProxy(event);
			}

			@Override
			public boolean onDragEvent(final DragEvent event) {
				final boolean[] result = { false };
				GlibContext.blockForMain(new Runnable() {
					@Override
					public void run() {
						result[0] = notifyDragEvent(event);
					}
				});
				return result[0];
			}

			@Override
			public boolean onCheckIsTextEditor() {
				return this.activeImContext != null;
			}

			@Override
			public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
				if (activeImContext == null) {
					return null;
				}
				outAttrs.contentMimeTypes = new String[] { "text/plain" };
				outAttrs.inputType = InputType.TYPE_NULL;
				outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN;
				return activeImContext.new ImeConnection(this);
			}

			@Override
			protected void onAttachedToWindow() {
				super.onAttachedToWindow();
				GlibContext.runOnMain(new Runnable() {
					@Override
					public void run() {
						notifyAttached();
					}
				});
			}

			@Override
			protected void onDetachedFromWindow() {
				super.onDetachedFromWindow();
				GlibContext.runOnMain(new Runnable() {
					@Override
					public void run() {
						notifyDetached();
					}
				});
			}

			@Override
			protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
				if (changed) {
					final int x = left - ToplevelView.this.insets.left;
					final int y = top - ToplevelView.this.insets.top;
					GlibContext.runOnMain(new Runnable() {
						@Override
						public void run() {
							notifyLayoutPosition(x, y);
						}
					});
				}
				super.onLayout(changed, left, top, right, bottom);
			}

			@Override
			public void surfaceCreated(SurfaceHolder holder) {
				// Must stay synchronous (not via GlibContext) so delayed_map is
				// set before surfaceChanged dispatches notifyLayoutSurface.
				notifyVisibility(true);
			}

			@Override
			public void surfaceChanged(SurfaceHolder holder, int format, final int width, final int height) {
				final float scale = getResources().getDisplayMetrics().density;
				GlibContext.blockForMain(new Runnable() {
					@Override
					public void run() {
						notifyLayoutSurface(width, height, scale);
					}
				});
			}

			@Override
			public void surfaceDestroyed(SurfaceHolder holder) {
				notifyVisibility(false);
			}
		}

		private class LayoutParams extends ViewGroup.LayoutParams {
			public int x;
			public int y;

			public LayoutParams(int x, int y, int width, int height) {
				super(width, height);
				this.x = x;
				this.y = y;
			}
		}

		public Surface toplevel;
		private Surface grabbed;

		private Insets insets = Insets.NONE;

		public ToplevelView() {
			super(ToplevelActivity.this);
		}

		@Override
		protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
			if (toplevel == null) {
				setMeasuredDimension(0, 0);
				return;
			}

			measureChildren(
					widthMeasureSpec - (insets.left + insets.right),
					heightMeasureSpec - (insets.top + insets.bottom)
			);
			setMeasuredDimension(widthMeasureSpec, heightMeasureSpec);
		}

		@Override
		protected void onLayout(boolean changed, int l, int t, int r, int b) {
			int count = getChildCount();
			for (int i = 0; i < count; i++) {
				View child = getChildAt(i);
				if (child.getVisibility() != GONE) {
					LayoutParams lp = (LayoutParams) child.getLayoutParams();
					child.layout(
							l + lp.x + insets.left,
							t + lp.y + insets.top,
							l + lp.x + insets.left + child.getMeasuredWidth(),
							t + lp.y + insets.top + child.getMeasuredHeight()
					);
				}
			}
		}

		@Override
		protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
			return p instanceof LayoutParams;
		}

		@Override
		public WindowInsets onApplyWindowInsets(WindowInsets insets) {
			// Upstream uses WindowInsets.Type (API 30) and includes the IME in
			// the inset so the GTK surface is not laid out under the keyboard.
			int left = insets.getSystemWindowInsetLeft();
			int top = insets.getSystemWindowInsetTop();
			int right = insets.getSystemWindowInsetRight();
			int bottom = insets.getSystemWindowInsetBottom();
			if (Build.VERSION.SDK_INT >= 30) {
				bottom = Math.max(bottom, Api30Insets.imeBottom(insets, bottom));
			}
			this.insets = Insets.of(left, top, right, bottom);
			requestLayout();
			// WindowInsets.CONSUMED is API 30; on older releases the insets
			// are already consumed by the return above, so hand back the same
			// instance the system gave us.
			if (Build.VERSION.SDK_INT >= 30) {
				return Api30Insets.consumed(insets);
			}
			return insets;
		}

		public void setGrabbedSurface(final Surface grabbed) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					ToplevelView.this.grabbed = grabbed;
				}
			});
		}

		public void pushPopup(final long surfaceIdentifier, final int x, final int y,
							  final int width, final int height) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					Surface surface = new Surface(surfaceIdentifier);
					addView(surface, new LayoutParams(x, y, width, height));
					requestLayout();
				}
			});
		}
	}

	/**
	 * Isolates the API 30 window-insets call so the class verifier never has to
	 * resolve android.view.WindowInsets$Type while running on API 29.
	 */
	private static final class Api30Insets {
		/**
		 * WindowInsets.CONSUMED only exists from API 30. The field is read
		 * reflectively so the class still loads (and the method still returns
		 * the right object) on API 29, which is what compileSdkVersion targets.
		 */
		static WindowInsets consumed(WindowInsets fallback) {
			try {
				return (WindowInsets) WindowInsets.class.getField("CONSUMED").get(null);
			} catch (Throwable ignored) {
				return fallback;
			}
		}

		static int imeBottom(WindowInsets insets, int fallback) {
			try {
				Object imeType = Class.forName("android.view.WindowInsets$Type")
						.getMethod("ime").invoke(null);
				int[] types = { ((Integer) imeType).intValue() };
				java.lang.reflect.Method getInsets =
						WindowInsets.class.getMethod("getInsets", int[].class);
				Insets resolved = (Insets) getInsets.invoke(insets, types);
				if (resolved != null) {
					return resolved.bottom;
				}
			} catch (Throwable ignored) {
				// Not available: keep the pre-30 behaviour.
			}
			return fallback;
		}
	}

	private long nativeIdentifier;

	@GlibContext.GtkThread
	private native void bindNative(long surface) throws UnregisteredSurfaceException;
	@GlibContext.GtkThread
	private native void notifyConfigurationChange();
	@GlibContext.GtkThread
	private native void notifyStateChange(boolean has_focus, boolean is_fullscreen);
	@GlibContext.GtkThread
	private native void notifyOnBackPress();
	@GlibContext.GtkThread
	private native void notifyDestroy();
	@GlibContext.GtkThread
	private native void notifyActivityResult(int requestCode, int resultCode, Intent result);

	private ToplevelView view;
	private boolean fullscreenState;

	/**
	 * Set by the host activity before {@code super.onCreate()} when the GDK
	 * android backend is not usable in this process. Every native method in
	 * this class is registered by {@code gdk_android_initialize()}, so calling
	 * one of them while it is false throws UnsatisfiedLinkError.
	 */
	public static volatile boolean gdkUnavailable = false;

	/**
	 * Every native method of this class is registered by
	 * {@code gdk_android_initialize()}. When that failed the whole GtkThread
	 * surface is missing, so calling one of them throws UnsatisfiedLinkError
	 * instead of doing nothing - the lifecycle callbacks below have to be able
	 * to ask first.
	 */
	private static boolean gdkCallable() {
		return !gdkUnavailable;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		this.fullscreenState = false;

		super.onCreate(savedInstanceState);
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER);
		// Pre-30 equivalent of setDecorFitsSystemWindows(false): let the GTK
		// surface cover the whole window including the system bar areas.
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

		this.view = new ToplevelView();
		setContentView(this.view);

		if (gdkUnavailable) {
			Log.e(TAG, "GDK android backend is not initialised, not binding this activity");
			return;
		}

		final long[] possibleIdentifiers = {
			savedInstanceState != null ? savedInstanceState.getLong(toplevelIdentifierKey, 0) : 0L,
			getIntent().getLongExtra(toplevelIdentifierKey, 0),
		};

		long savedIdentifier = 0;
		for (long identifier : possibleIdentifiers) {
			if (identifier != 0) {
				savedIdentifier = identifier;
				break;
			}
		}

		if (savedIdentifier != 0) {
			/*
			 * A window this activity used to own is still registered in GDK, so
			 * the GTK main loop is up (it lives inside QEMU's gtk_init()). GDK
			 * requires those calls on its own thread, hence runOnMain.
			 *
			 * The wait is bounded on purpose. The previous code awaited an
			 * unbounded CountDownLatch here, and on the first launch - when no
			 * identifier exists yet and the GTK loop has not started - that
			 * never returned: the UI thread was wedged forever. That is the
			 * "start hangs, screen goes black" report (the process was later
			 * killed by gtk_init()'s exit(1)).
			 */
			final long identifier = savedIdentifier;
			final CountDownLatch barrier = new CountDownLatch(1);
			GlibContext.runOnMain(new Runnable() {
				@Override
				public void run() {
					try {
						bindNative(identifier);
					} catch (UnregisteredSurfaceException e) {
						nativeIdentifier = 0;
					} catch (Throwable t) {
						nativeIdentifier = 0;
						Log.w(TAG, "Could not rebind the GTK surface: " + t);
					} finally {
						barrier.countDown();
					}
				}
			});
			try {
				if (!barrier.await(2, TimeUnit.SECONDS)) {
					Log.e(TAG, "GTK did not rebind the surface in time");
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			if (nativeIdentifier != 0) {
				return;
			}
		}

		/*
		 * First window in this process (or the rebind failed). Nothing to do
		 * here: the host activity was already handed to gdk_android_initialize(),
		 * which called gdk_android_set_latest_activity() for it, and the
		 * gdk_android_toplevel_present() backend path claims that unbound
		 * activity by itself (it calls bindNative() and skips spawning a new
		 * one) as soon as QEMU presents its window.
		 *
		 * GdkContext.activate()/open() are deliberately not used: they go
		 * through g_application_activate(g_application_get_default()), and QEMU
		 * is not a GApplication, so the default application is NULL and the call
		 * does nothing.
		 */
		try {
			GdkContext._set_latest_activity(ToplevelActivity.this);
		} catch (Throwable t) {
			Log.w(TAG, "Could not register this activity as the GTK host: " + t);
		}
	}

	/**
	 * Called by the GDK android backend (by name) once it has registered the
	 * native identifier of the window this activity owns.
	 */
	private void attachToplevelSurface() {
		if (this.view.toplevel != null) {
			this.view.toplevel.drop();
		}
		this.view.toplevel = this.view.new Surface(this.nativeIdentifier);
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				view.addView(view.toplevel, view.new LayoutParams(
						0, 0,
						ViewGroup.LayoutParams.MATCH_PARENT,
						ViewGroup.LayoutParams.MATCH_PARENT
				));
				view.requestLayout();
				// Give the GTK surface focus (mirroring SDL's explicit
				// requestFocus). Otherwise Android hands key events to whichever
				// focusable sibling took focus first and the guest gets nothing.
				view.toplevel.post(new Runnable() {
					@Override
					public void run() {
						if (view.toplevel != null) {
							view.toplevel.requestFocus();
						}
					}
				});
			}
		});
	}

	private void updateToplevelState() {
		if (!gdkCallable()) {
			return;
		}
		final boolean hasFocus = hasWindowFocus();
		final boolean isFullscreen = this.fullscreenState;
		GlibContext.runOnMain(new Runnable() {
			@Override
			public void run() {
				notifyStateChange(hasFocus, isFullscreen);
			}
		});
	}

	@SuppressWarnings("deprecation")
	@Override
	public void onBackPressed() {
		if (!gdkCallable()) {
			super.onBackPressed();
			return;
		}
		GlibContext.runOnMain(new Runnable() {
			@Override
			public void run() {
				notifyOnBackPress();
			}
		});
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		if (!gdkCallable()) {
			return;
		}
		GlibContext.runOnMain(new Runnable() {
			@Override
			public void run() {
				notifyConfigurationChange();
			}
		});
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		// The GTK surface may not be bound yet (nativeIdentifier == 0). In that
		// case notifyStateChange() looks up a NULL native pointer and crashes,
		// so skip the update until the backend has bound a window.
		if (this.nativeIdentifier != 0) {
			updateToplevelState();
			if (hasFocus && this.view != null && this.view.toplevel != null) {
				this.view.toplevel.post(new Runnable() {
					@Override
					public void run() {
						if (view.toplevel != null) {
							view.toplevel.requestFocus();
						}
					}
				});
			}
		}
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		if (this.nativeIdentifier != 0) {
			outState.putLong(toplevelIdentifierKey, this.nativeIdentifier);
		}
		super.onSaveInstanceState(outState);
	}

	@Override
	protected void onDestroy() {
		if (isFinishing() && gdkCallable()) {
			GlibContext.runOnMain(new Runnable() {
				@Override
				public void run() {
					notifyDestroy();
				}
			});
		}
		super.onDestroy();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
		if (!gdkCallable()) {
			super.onActivityResult(requestCode, resultCode, data);
			return;
		}
		final int request = requestCode;
		final int result = resultCode;
		GlibContext.runOnMain(new Runnable() {
			@Override
			public void run() {
				notifyActivityResult(request, result, data);
			}
		});
	}

	public void postWindowConfiguration(final int color, final boolean fullscreen) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Window window = getWindow();
				view.setBackgroundColor(color);

				boolean darkFg = (((color >> 16) & 0xFF) * 0.299 +
						((color >> 8) & 0xFF) * 0.587 +
						((color) & 0xFF) * 0.114) > 186;

				fullscreenState = fullscreen;
				if (fullscreen) {
					window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
					setSystemBarsVisible(window, false);
				} else {
					window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
					setSystemBarsVisible(window, true);
				}
				updateToplevelState();
			}
		});
	}

	/**
	 * API 30's WindowInsetsController has no pre-30 equivalent, so it is used
	 * reflectively (the class only exists from API 30 on) with the legacy
	 * systemUiVisibility flags as the fallback.
	 */
	@SuppressLint("InlinedApi")
	private void setSystemBarsVisible(Window window, boolean visible) {
		if (Build.VERSION.SDK_INT >= 30) {
			try {
				Object controller = Window.class.getMethod("getInsetsController")
						.invoke(window);
				Object systemBars = Class.forName("android.view.WindowInsets$Type")
						.getMethod("systemBars").invoke(null);
				int[] types = { ((Integer) systemBars).intValue() };
				Class<?> controllerClass =
						Class.forName("android.view.WindowInsetsController");
				controllerClass.getMethod(visible ? "show" : "hide", int[].class)
						.invoke(controller, types);
				return;
			} catch (Throwable ignored) {
				// Fall through to the legacy flags.
			}
		}
		int flags = visible
				? View.SYSTEM_UI_FLAG_VISIBLE
				: (View.SYSTEM_UI_FLAG_FULLSCREEN
					| View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
					| View.SYSTEM_UI_FLAG_LAYOUT_STABLE
					| View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
					| View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
					| View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
		window.getDecorView().setSystemUiVisibility(flags);
	}

	public void postTitle(final String title) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				setTitle(title);
				ActivityManager.TaskDescription desc = new ActivityManager.TaskDescription(title);
				setTaskDescription(desc);
			}
		});
	}
}
