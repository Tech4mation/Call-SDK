package com.nativetalk.call.utils

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.text.format.Formatter.formatShortFileSize
import android.util.TypedValue
import androidx.core.content.res.ResourcesCompat
import androidx.emoji.text.EmojiCompat
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.nativetalk.call.NativetalkCallSDK.coreContext
import java.util.*
import com.nativetalk.call.R
import org.linphone.core.tools.Log

/**
 * Various utility methods for application
 */
class AppUtils {
    companion object {
        const val SOCKET_BASE_URL = "http://messaging.nativetalkapp.com"
        fun getString(id: Int): String {
            return coreContext.context.getString(id)
        }

        fun getStringWithPlural(id: Int, count: Int): String {
            return coreContext.context.resources.getQuantityString(id, count, count)
        }

        fun getStringWithPlural(id: Int, count: Int, value: String): String {
            return coreContext.context.resources.getQuantityString(id, count, value)
        }

        fun getDimension(id: Int): Float {
            return coreContext.context.resources.getDimension(id)
        }

        fun getInitials(displayName: String, limit: Int = 2): String {
            if (displayName.isEmpty()) return ""

            val split = displayName.uppercase(Locale.getDefault()).split(" ")
            var initials = ""
            var characters = 0

            val emoji = try {
                EmojiCompat.get()
            } catch (ise: IllegalStateException) {
                Log.e("[App Utils] Can't get EmojiCompat: $ise")
                null
            }

            for (i in split.indices) {
                if (split[i].isNotEmpty()) {
                    try {
                        if (emoji?.hasEmojiGlyph(split[i]) == true) {
                            initials += emoji.process(split[i])
                        } else {
                            initials += split[i][0]
                        }
                    } catch (ise: IllegalStateException) {
                        Log.e("[App Utils] Can't call hasEmojiGlyph: $ise")
                        initials += split[i][0]
                    }

                    characters += 1
                    if (characters >= limit) break
                }
            }
            return initials
        }

        fun dpToPixels(context: Context, dp: Float): Float {
            return dp * context.resources.displayMetrics.density
        }

        fun pixelsToDp(pixels: Float): Float {
            return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                pixels,
                coreContext.context.resources.displayMetrics
            )
        }


        fun getDividerDecoration(context: Context, layoutManager: LinearLayoutManager): DividerItemDecoration {
            val dividerItemDecoration = DividerItemDecoration(context, layoutManager.orientation)
            val divider = ResourcesCompat.getDrawable(context.resources, R.drawable.divider, null)
            if (divider != null) dividerItemDecoration.setDrawable(divider)
            return dividerItemDecoration
        }

        fun isMediaVolumeLow(context: Context): Boolean {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            Log.i("[Media Volume] Current value is $currentVolume, max value is $maxVolume")
            return currentVolume <= maxVolume * 0.5
        }

        fun acquireAudioFocusForVoiceRecordingOrPlayback(context: Context): AudioFocusRequestCompat {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val audioAttrs = AudioAttributesCompat.Builder()
                .setUsage(AudioAttributesCompat.USAGE_MEDIA)
                .setContentType(AudioAttributesCompat.CONTENT_TYPE_SPEECH)
                .build()

            val request =
                AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    .setAudioAttributes(audioAttrs)
                    .setOnAudioFocusChangeListener { }
                    .build()
            when (AudioManagerCompat.requestAudioFocus(audioManager, request)) {
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
                    Log.i("[Audio Focus] Voice recording/playback audio focus request granted")
                }
                AudioManager.AUDIOFOCUS_REQUEST_FAILED -> {
                    Log.w("[Audio Focus] Voice recording/playback audio focus request failed")
                }
                AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> {
                    Log.w("[Audio Focus] Voice recording/playback audio focus request delayed")
                }
            }
            return request
        }

        fun releaseAudioFocusForVoiceRecordingOrPlayback(
            context: Context,
            request: AudioFocusRequestCompat
        ) {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            AudioManagerCompat.abandonAudioFocusRequest(audioManager, request)
            Log.i("[Audio Focus] Voice recording/playback audio focus request abandoned")
        }
    }
}
