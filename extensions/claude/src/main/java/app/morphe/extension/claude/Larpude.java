package app.morphe.extension.claude;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
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
 * A Settings row in the app is an obfuscated class with 5 fields:
 * a = id (String), b = label (String), c = icon, d = subtitle (String), e = action.
 * It is built through reflection because that class does not exist at compile time.
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
            if (ID.equals(read(first, "a"))) return rows; // already added

            Class<?> rowClass = first.getClass();
            Field iconField = field(rowClass, "c");
            Field actionField = field(rowClass, "e");

            Constructor<?> constructor = rowClass.getDeclaredConstructor(
                    String.class, String.class, iconField.getType(), String.class, actionField.getType());
            constructor.setAccessible(true);

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
            if (row != null && ID.equals(read(row, "a"))) {
                Activity activity = current.get();
                if (activity != null) LarpudeScreen.show(activity);
                return null;
            }
        } catch (Throwable t) {
            Log.e(TAG, "interceptRow failed", t);
        }
        return row;
    }

    private static Field field(Class<?> c, String name) throws NoSuchFieldException {
        Field f = c.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    private static Object read(Object o, String fieldName) throws Exception {
        return field(o.getClass(), fieldName).get(o);
    }
}
