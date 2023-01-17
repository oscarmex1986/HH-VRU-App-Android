package se.astazero.androidmqttapp

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.google.android.gms.location.DetectedActivity
import java.lang.IllegalArgumentException

const val SUPPORTED_ACTIVITY_KEY = "activity_key"

enum class SupportedActivity(
    @StringRes val activityText: Int
) {

    NOT_STARTED(R.string.time_to_start),
    STILL(R.string.still_text),
    WALKING(R.string.walking_text),
    CYCLING(R.string.cycling_text),
    AUTOMOTIVE(R.string.automotive_text),
    RUNNING(R.string.running_text);

    companion object {

        fun fromActivityType(type: Int): SupportedActivity = when (type) {
            DetectedActivity.STILL -> STILL
            DetectedActivity.WALKING -> WALKING
            DetectedActivity.RUNNING -> RUNNING
            DetectedActivity.ON_BICYCLE -> CYCLING
            DetectedActivity.IN_VEHICLE -> AUTOMOTIVE
            else -> throw IllegalArgumentException("activity $type not supported")
        }
    }
}