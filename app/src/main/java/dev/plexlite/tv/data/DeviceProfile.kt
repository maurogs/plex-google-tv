package dev.plexlite.tv.data

import android.media.MediaCodecList

object DeviceProfile {

    private val mimeToPlex = mapOf(
        "video/avc" to "h264",
        "video/hevc" to "hevc",
        "video/av01" to "av1",
        "video/x-vnd.on2.vp9" to "vp9",
        "video/x-vnd.on2.vp8" to "vp8",
        "video/mpeg2" to "mpeg2video",
        "video/mp4v-es" to "mpeg4",
        "video/3gpp" to "h263",
        "video/x-ms-wmv" to "vc1",
        "video/wvc1" to "vc1",
    )

    private val audioCodecs = listOf(
        "aac", "ac3", "eac3", "dca", "dts", "dca-ma", "truehd", "mlp", "flac", "alac",
        "mp3", "mp2", "opus", "vorbis", "pcm", "wmav2", "wmapro",
    )

    private val subtitleCodecs = listOf(
        "srt", "subrip", "ass", "ssa", "pgs", "hdmv_pgs_subtitle", "vobsub", "dvd_subtitle",
        "dvb_subtitle", "webvtt", "vtt", "mov_text", "tx3g",
    )

    private val containers = listOf("mkv", "matroska", "mp4", "m4v", "mov", "avi", "mpegts", "ts", "webm")

    val videoCodecs: List<String> by lazy {
        val detected = runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                .filter { !it.isEncoder }
                .flatMap { it.supportedTypes.toList() }
                .mapNotNull { mimeToPlex[it.lowercase()] }
        }.getOrDefault(emptyList())
        (listOf("h264") + detected).distinct()
    }

    val profileExtra: String by lazy {
        listOf(
            "add-direct-play-profile(type=videoProfile&container=${containers.joinToString(",")}" +
                "&videoCodec=${videoCodecs.joinToString(",")}" +
                "&audioCodec=${audioCodecs.joinToString(",")}" +
                "&subtitleCodec=${subtitleCodecs.joinToString(",")})",
            "add-transcode-target(type=videoProfile&context=streaming&protocol=hls&container=mpegts" +
                "&videoCodec=h264&audioCodec=aac,ac3,eac3&subtitleCodec=)",
        ).joinToString("+")
    }
}
