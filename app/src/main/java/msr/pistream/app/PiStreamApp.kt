package msr.pistream.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import msr.pistream.app.crash.CrashReporter

/** App entry point: installs the global crash reporter + UI tracking. */
class PiStreamApp : Application() {

    private var defaultHandler: Thread.UncaughtExceptionHandler? = null

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                trackTouches(activity)
            }

            override fun onActivityStarted(activity: Activity) {}

            override fun onActivityResumed(activity: Activity) {
                CrashReporter.currentActivity = activity.javaClass.simpleName
            }

            override fun onActivityPaused(activity: Activity) {}

            override fun onActivityStopped(activity: Activity) {}

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

            override fun onActivityDestroyed(activity: Activity) {}
        })

        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                CrashReporter.save(this, thread, throwable)
            } catch (_: Exception) {
                // never let the reporter itself break the crash path
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    /** Records the last touched view (where the user tapped before the crash). */
    private fun trackTouches(activity: Activity) {
        activity.window.decorView.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                CrashReporter.lastTouch = describeView(v)
            }
            false
        }
    }

    private fun describeView(v: View): String {
        val sb = StringBuilder(v.javaClass.simpleName)
        if (v.id != View.NO_ID) {
            try {
                sb.append(" id=").append(resources.getResourceEntryName(v.id))
            } catch (_: Exception) {
                // no resource name for this id
            }
        }
        if (v is TextView) {
            v.text?.toString()?.take(40)?.let { sb.append(" text=").append(it) }
        }
        v.contentDescription?.let { sb.append(" desc=").append(it) }
        return sb.toString()
    }
}
