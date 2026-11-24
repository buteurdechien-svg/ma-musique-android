package com.ahize.mamusique.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.nio.ByteBuffer

/**
 * Extrait la piste son d'une vidéo sans la réencoder (aucune perte de qualité)
 * et l'enregistre dans Musique/MaMusique.
 */
object AudioExtractor {

    fun extract(context: Context, video: File): Uri? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(video.path)
            var trackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    trackIndex = i
                    format = f
                    break
                }
            }
            if (trackIndex < 0 || format == null) return null

            val (muxerFormat, extension, outMime) = when (format.getString(MediaFormat.KEY_MIME)) {
                MediaFormat.MIMETYPE_AUDIO_AAC -> Triple(MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4, "m4a", "audio/mp4")
                MediaFormat.MIMETYPE_AUDIO_OPUS -> Triple(MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG, "ogg", "audio/ogg")
                MediaFormat.MIMETYPE_AUDIO_VORBIS -> Triple(MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM, "webm", "audio/webm")
                else -> return null
            }

            extractor.selectTrack(trackIndex)
            val temp = File(context.cacheDir, "extrait_${System.currentTimeMillis()}.$extension")
            val muxer = MediaMuxer(temp.path, muxerFormat)
            val outTrack = muxer.addTrack(format)
            muxer.start()
            val buffer = ByteBuffer.allocate(2 * 1024 * 1024)
            val info = MediaCodec.BufferInfo()
            while (true) {
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                info.offset = 0
                info.size = size
                info.presentationTimeUs = extractor.sampleTime
                info.flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                    MediaCodec.BUFFER_FLAG_KEY_FRAME
                } else 0
                muxer.writeSampleData(outTrack, buffer, info)
                extractor.advance()
            }
            muxer.stop()
            muxer.release()

            val baseName = video.nameWithoutExtension.ifBlank { "Audio" }
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, "$baseName.$extension")
                put(MediaStore.Audio.Media.TITLE, baseName)
                put(MediaStore.Audio.Media.MIME_TYPE, outMime)
                put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/MaMusique")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = resolver.insert(collection, values) ?: return null
            resolver.openOutputStream(uri)?.use { out -> temp.inputStream().use { it.copyTo(out) } }
            values.clear()
            values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            temp.delete()
            return uri
        } finally {
            extractor.release()
        }
    }
}
