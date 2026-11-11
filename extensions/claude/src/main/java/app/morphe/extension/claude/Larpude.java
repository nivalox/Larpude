package app.morphe.extension.claude;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Larpude entry point.
 *
 * The patch hooks three places in the Claude app (see LarpudePatch.kt):
 *  - MainActivity.onCreate           -> {@link #onMainActivityCreate}
 *  - the list of Settings rows       -> {@link #addRow}
 *  - the Settings row tap handler    -> {@link #interceptRow}
 *
 * A Settings row in the app is an obfuscated class built as (String id, String label, icon,
 * String subtitle, action). Its names change per release, so it is handled through reflection by
 * field/constructor TYPE, never by name.
 */
public final class Larpude {
    private static final String TAG = "Larpude";
    private static final String ID = "Larpude";
    private static final String LABEL = "Larpude";

    private static boolean registered;
    private static WeakReference<Activity> current = new WeakReference<>(null);

    private Larpude() {}

    /** Hooked into MainActivity.onCreate. */
    public static void onMainActivityCreate(Activity activity) {
        current = new WeakReference<>(activity);
        if (registered) return;
        registered = true;
        activity.getApplication().registerActivityLifecycleCallbacks(
                new Application.ActivityLifecycleCallbacks() {
                    @Override public void onActivityResumed(Activity a) {
                        current = new WeakReference<>(a);
                    }
                    @Override public void onActivityCreated(Activity a, Bundle b) {}
                    @Override public void onActivityStarted(Activity a) {}
                    @Override public void onActivityPaused(Activity a) {}
                    @Override public void onActivityStopped(Activity a) {}
                    @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
                    @Override public void onActivityDestroyed(Activity a) {}
                });
    }

    /** Hooked at the end of the method that builds the Settings row list. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static List addRow(List rows) {
        try {
            if (rows == null || rows.isEmpty()) return rows;
            Object first = rows.get(0);
            if (isLarpude(first)) return rows; // already added

            Class<?> rowClass = first.getClass();
            Constructor<?> constructor = null;
            for (Constructor<?> c : rowClass.getDeclaredConstructors()) {
                if (c.getParameterTypes().length == 5) { constructor = c; break; }
            }
            if (constructor == null) throw new NoSuchMethodException("5-arg row constructor");
            constructor.setAccessible(true);

            Class<?>[] params = constructor.getParameterTypes();
            Field iconField = fieldOfType(rowClass, params[2]);
            Field actionField = fieldOfType(rowClass, params[4]);

            // Reuse the first row's icon and action object; the tap is intercepted anyway.
            Object row = constructor.newInstance(
                    ID, LABEL, iconField.get(first), null, actionField.get(first));

            ArrayList out = new ArrayList(rows.size() + 1);
            out.add(row);
            out.addAll(rows);
            return out;
        } catch (Throwable t) {
            Log.e(TAG, "Could not add the Larpude row", t);
            return rows;
        }
    }

    /**
     * Hooked into the Settings row tap handler. Gets the tapped row (or null).
     * For our row: show the Hello screen and return null, so the app does nothing more.
     * For any other row: return it untouched.
     */
    public static Object interceptRow(Object row) {
        try {
            if (isLarpude(row)) {
                Activity activity = current.get();
                if (activity != null) LarpudeScreen.show(activity);
                return null;
            }
        } catch (Throwable t) {
            Log.e(TAG, "interceptRow failed", t);
        }
        return row;
    }

    /** True if any String field of the row equals our id (works whatever the field names are). */
    private static boolean isLarpude(Object row) throws Exception {
        if (row == null) return false;
        for (Field f : row.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || f.getType() != String.class) continue;
            f.setAccessible(true);
            if (ID.equals(f.get(row))) return true;
        }
        return false;
    }

    private static Field fieldOfType(Class<?> c, Class<?> type) throws NoSuchFieldException {
        for (Field f : c.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers()) && f.getType() == type) {
                f.setAccessible(true);
                return f;
            }
        }
        throw new NoSuchFieldException(type.getName());
    }
}
