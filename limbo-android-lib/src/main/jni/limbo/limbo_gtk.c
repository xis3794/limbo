/*
 * limbo_gtk.c - GTK4 "android" GDK backend bootstrap for the Limbo emulator.
 *
 * QEMU's GTK display backend (ui/gtk.c, enabled by the GTK4 port patch) needs
 * GDK's android backend before it can create a window: that backend has to be
 * handed the JNI environment, the application class loader and the host
 * activity once, and it caches them.
 *
 * libgtk-4.so is resolved with dlopen() at RUNTIME on purpose. Linking it here
 * would make liblimbo.so unloadable in builds without the GTK stack (the whole
 * point of USE_GTK being optional), and it would create a link-order cycle
 * (compat -> glib -> pixman -> gtk -> back into the JNI layer).
 *
 * gtk_init() is deliberately NOT called: GTK is single threaded, and QEMU's
 * ui/gtk.c calls gtk_init_check() itself on the thread that then runs the main
 * loop. Initialising GTK from the Android UI thread here would bind it to the
 * wrong thread.
 *
 * This file is compiled into liblimbo.so by limbo/Android.mk.
 */

#include <jni.h>
#include <dlfcn.h>
#include <android/log.h>
#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/types.h>

#define LOG_TAG "limbo-gtk"

/* GDK's android entry point, exported by libgtk-4.so. */
typedef int (*gdk_android_initialize_fn)(JNIEnv *env,
                                         jobject application_classloader,
                                         jobject activity);

/* Set once the backend accepted the binding; the Java side polls it. */
static jboolean gtk_android_initialized = JNI_FALSE;

/* mkdir -p, returning 0 on success. */
static int limbo_mkdir_p(const char *path)
{
    char tmp[1024];
    size_t len, i;

    if (path == NULL) {
        return -1;
    }
    len = strlen(path);
    if (len == 0 || len >= sizeof(tmp)) {
        return -1;
    }
    memcpy(tmp, path, len + 1);

    for (i = 1; i < len; i++) {
        if (tmp[i] == '/') {
            tmp[i] = '\0';
            if (mkdir(tmp, 0755) != 0 && errno != EEXIST) {
                return -1;
            }
            tmp[i] = '/';
        }
    }
    if (mkdir(tmp, 0755) != 0 && errno != EEXIST) {
        return -1;
    }
    return 0;
}

/* Absolute path of <cacheDir>/limbo/var/tmp, or NULL. Caller frees. */
static char *limbo_tmp_dir(JNIEnv *env, jobject activity)
{
    jclass cls;
    jmethodID mid;
    jobject file;
    jstring jpath;
    const char *cache_dir;
    char *out;
    size_t need;

    if (activity == NULL) {
        return NULL;
    }
    cls = (*env)->GetObjectClass(env, activity);
    if (cls == NULL) {
        return NULL;
    }
    mid = (*env)->GetMethodID(env, cls, "getCacheDir", "()Ljava/io/File;");
    if (mid == NULL) {
        return NULL;
    }
    file = (*env)->CallObjectMethod(env, activity, mid);
    if (file == NULL) {
        return NULL;
    }
    cls = (*env)->GetObjectClass(env, file);
    if (cls == NULL) {
        return NULL;
    }
    mid = (*env)->GetMethodID(env, cls, "getAbsolutePath", "()Ljava/lang/String;");
    if (mid == NULL) {
        return NULL;
    }
    jpath = (jstring) (*env)->CallObjectMethod(env, file, mid);
    if (jpath == NULL) {
        return NULL;
    }
    cache_dir = (*env)->GetStringUTFChars(env, jpath, NULL);
    if (cache_dir == NULL) {
        return NULL;
    }

    /* "<cache>/limbo/var/tmp" + NUL */
    need = strlen(cache_dir) + sizeof("/limbo/var/tmp");
    out = (char *) malloc(need);
    if (out != NULL) {
        snprintf(out, need, "%s/limbo/var/tmp", cache_dir);
    }
    (*env)->ReleaseStringUTFChars(env, jpath, cache_dir);
    return out;
}

/*
 * Android has no /tmp, so GLib's g_get_tmp_dir() returns a path that does not
 * exist and every mkstemp()/memfd fallback fails with ENOENT. Point TMPDIR at
 * the app's own cache dir before QEMU (and therefore GLib) starts.
 */
static void limbo_setup_tmpdir(JNIEnv *env, jobject activity)
{
    char *tmpdir;

    tmpdir = limbo_tmp_dir(env, activity);
    if (tmpdir == NULL) {
        return;
    }
    if (limbo_mkdir_p(tmpdir) == 0) {
        setenv("TMPDIR", tmpdir, 1);
        __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "TMPDIR=%s", tmpdir);
    } else {
        __android_log_print(ANDROID_LOG_WARN, LOG_TAG,
                            "could not create %s", tmpdir);
    }
    free(tmpdir);
}

JNIEXPORT jboolean JNICALL
Java_com_max2idea_android_limbo_main_LimboGtk_nativeHasInit(JNIEnv *env, jclass klass)
{
    (void) env;
    (void) klass;
    return gtk_android_initialized;
}

JNIEXPORT jboolean JNICALL
Java_com_max2idea_android_limbo_main_LimboGtk_nativeInit(
        JNIEnv *env, jclass klass, jobject class_loader, jobject activity)
{
    void *handle;
    gdk_android_initialize_fn init_fn;

    (void) klass;

    if (class_loader == NULL || activity == NULL) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
                            "class loader or activity is NULL");
        return JNI_FALSE;
    }

    limbo_setup_tmpdir(env, activity);

    handle = dlopen("libgtk-4.so", RTLD_NOW | RTLD_GLOBAL);
    if (handle == NULL) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
                            "dlopen(libgtk-4.so) failed: %s", dlerror());
        return JNI_FALSE;
    }

    init_fn = (gdk_android_initialize_fn) dlsym(handle, "gdk_android_initialize");
    if (init_fn == NULL) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
                            "dlsym(gdk_android_initialize) failed: %s", dlerror());
        return JNI_FALSE;
    }

    if (!init_fn(env, class_loader, activity)) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
                            "gdk_android_initialize() failed");
        return JNI_FALSE;
    }

    gtk_android_initialized = JNI_TRUE;
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG,
                        "GDK android backend initialized (-display gtk usable)");
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_max2idea_android_limbo_main_LimboGtk_nativeSetEnv(
        JNIEnv *env, jclass klass, jstring jkey, jstring jvalue)
{
    const char *key;
    const char *value;

    (void) klass;

    if (jkey == NULL || jvalue == NULL) {
        return;
    }
    key = (*env)->GetStringUTFChars(env, jkey, NULL);
    value = (*env)->GetStringUTFChars(env, jvalue, NULL);
    if (key != NULL && value != NULL) {
        setenv(key, value, 1);
        __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "env %s=%s", key, value);
    }
    if (key != NULL) {
        (*env)->ReleaseStringUTFChars(env, jkey, key);
    }
    if (value != NULL) {
        (*env)->ReleaseStringUTFChars(env, jvalue, value);
    }
}
