package com.nativetalk.call.activities.voip.data

import androidx.lifecycle.MutableLiveData
import java.text.DecimalFormat
import org.linphone.core.*
import com.nativetalk.call.R

enum class StatType(val nameResource: Int) {
    CAPTURE(R.string.call_stats_capture_filter),
    PLAYBACK(R.string.call_stats_player_filter),
    PAYLOAD(R.string.call_stats_codec),
    ENCODER(R.string.call_stats_encoder_name),
    DECODER(R.string.call_stats_decoder_name),
    DOWNLOAD_BW(R.string.call_stats_download),
    UPLOAD_BW(R.string.call_stats_upload),
    ICE(R.string.call_stats_ice),
    IP_FAM(R.string.call_stats_ip),
    SENDER_LOSS(R.string.call_stats_sender_loss_rate),
    RECEIVER_LOSS(R.string.call_stats_receiver_loss_rate),
    JITTER(R.string.call_stats_jitter_buffer)
}

class StatItemData(val type: StatType) {
    companion object {
        fun audioDeviceToString(device: AudioDevice?): String {
            return device?.let { "${it.deviceName} [${it.type}] (${it.driverName})" } ?: "null"
        }
    }

    val value = MutableLiveData<String>()

    fun update(call: Call, stats: CallStats) {
        val payloadType = call.currentParams.usedAudioPayloadType ?: return
        value.value = when (type) {
            StatType.CAPTURE -> audioDeviceToString(call.inputAudioDevice)
            StatType.PLAYBACK -> audioDeviceToString(call.outputAudioDevice)
            StatType.PAYLOAD -> "${payloadType.mimeType}/${payloadType.clockRate / 1000} kHz"
            StatType.ENCODER -> call.core.mediastreamerFactory.getEncoderText(payloadType.mimeType)
            StatType.DECODER -> call.core.mediastreamerFactory.getDecoderText(payloadType.mimeType)
            StatType.DOWNLOAD_BW -> "${stats.downloadBandwidth} kbits/s"
            StatType.UPLOAD_BW -> "${stats.uploadBandwidth} kbits/s"
            StatType.ICE -> stats.iceState.toString()
            StatType.IP_FAM -> if (stats.ipFamilyOfRemote == AddressFamily.Inet6) "IPv6" else "IPv4"
            StatType.SENDER_LOSS -> DecimalFormat("##.##%").format(stats.senderLossRate)
            StatType.RECEIVER_LOSS -> DecimalFormat("##.##%").format(stats.receiverLossRate)
            StatType.JITTER -> DecimalFormat("##.## ms").format(stats.jitterBufferSizeMs)
        }
    }
}
