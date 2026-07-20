package com.example.myapplication

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecordingManager(private val context: Context) {

    enum class State { IDLE, RECORDING }

    var state: State = State.IDLE
        private set

    var currentFilePath: String? = null
        private set

    private var recorder: MediaRecorder? = null
    private var startTimestamp: Long = 0L

    private val recordingDir: File by lazy {
        File(context.filesDir, "recordings").apply { mkdirs() }
    }

    @Synchronized
    fun startRecording(optionsJson: String?): String {
        if (state == State.RECORDING) {
            return error("ALREADY_RECORDING", "Recording is already in progress")
        }

        val options = parseOptions(optionsJson)
        val format = options.format.lowercase()
        val extension = if (format == "wav") "wav" else "m4a"
        val fileName = "rec_${TIMESTAMP_FORMAT.format(Date())}.$extension"
        val outputFile = File(recordingDir, fileName)

        return try {
            recorder = createMediaRecorder(format, outputFile).apply {
                prepare()
                start()
            }
            currentFilePath = outputFile.absolutePath
            startTimestamp = System.currentTimeMillis()
            state = State.RECORDING
            success(JSONObject().apply {
                put("message", "Recording started")
                put("format", format)
            })
        } catch (e: Exception) {
            recorder?.release()
            recorder = null
            outputFile.delete()
            error("START_FAILED", "Failed to start recording: ${e.message}")
        }
    }

    @Synchronized
    fun stopRecording(): String {
        if (state != State.RECORDING || recorder == null) {
            return error("NOT_RECORDING", "No recording in progress")
        }

        val filePath = currentFilePath ?: return error("INTERNAL_ERROR", "No output file")

        return try {
            recorder?.stop()
            recorder?.release()
            recorder = null
            state = State.IDLE

            val file = File(filePath)
            val duration = System.currentTimeMillis() - startTimestamp
            currentFilePath = null

            success(JSONObject().apply {
                put("filePath", filePath)
                put("duration", duration)
                put("fileSize", file.length())
                put("format", if (filePath.endsWith(".wav")) "wav" else "aac")
            })
        } catch (e: Exception) {
            recorder?.release()
            recorder = null
            state = State.IDLE
            currentFilePath = null
            error("STOP_FAILED", "Failed to stop recording: ${e.message}")
        }
    }

    @Synchronized
    fun cancelRecording(): String {
        if (state != State.RECORDING) {
            return error("NOT_RECORDING", "No recording in progress")
        }

        val filePath = currentFilePath
        try {
            recorder?.reset()
            recorder?.release()
        } catch (_: Exception) { }
        recorder = null
        state = State.IDLE
        currentFilePath = null

        if (filePath != null) {
            File(filePath).delete()
        }

        return success(JSONObject().apply {
            put("message", "Recording cancelled")
        })
    }

    @Synchronized
    fun getRecordingState(): String {
        val data = JSONObject().apply {
            put("state", state.name.lowercase())
            put("filePath", currentFilePath ?: JSONObject.NULL)
            if (state == State.RECORDING) {
                put("duration", System.currentTimeMillis() - startTimestamp)
            }
        }
        return success(data)
    }

    @Synchronized
    fun readRecordingFile(filePath: String?): String {
        val safePath = filePath?.takeIf { it.isNotBlank() }
            ?: return error("INVALID_PATH", "filePath is empty")

        return try {
            val file = File(safePath)
            val recordingRoot = recordingDir.canonicalFile
            val target = file.canonicalFile
            if (!target.path.startsWith(recordingRoot.path + File.separator)) {
                return error("INVALID_PATH", "filePath is outside recording directory")
            }
            if (!target.exists() || !target.isFile) {
                return error("FILE_NOT_FOUND", "Recording file does not exist")
            }

            val mimeType = if (target.extension.equals("wav", ignoreCase = true)) {
                "audio/wav"
            } else {
                "audio/mp4"
            }
            success(JSONObject().apply {
                put("fileName", target.name)
                put("mimeType", mimeType)
                put("fileSize", target.length())
                put("base64", Base64.encodeToString(target.readBytes(), Base64.NO_WRAP))
            })
        } catch (e: Exception) {
            error("READ_FAILED", "Failed to read recording file: ${e.message}")
        }
    }

    fun cleanupAllRecordings() {
        recordingDir.listFiles()?.forEach { it.delete() }
    }

    fun shutdown() {
        try {
            recorder?.reset()
            recorder?.release()
        } catch (_: Exception) { }
        recorder = null
        state = State.IDLE
        currentFilePath = null
    }

    private fun createMediaRecorder(format: String, outputFile: File): MediaRecorder {
        val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        mr.setAudioSource(MediaRecorder.AudioSource.MIC)

        when (format) {
            "wav" -> {
                mr.setOutputFormat(MediaRecorder.OutputFormat.DEFAULT)
                mr.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT)
            }
            else -> {
                mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            }
        }

        mr.setAudioSamplingRate(44100)
        mr.setAudioEncodingBitRate(128_000)
        mr.setAudioChannels(2)
        mr.setOutputFile(outputFile.absolutePath)

        mr.setOnErrorListener { _, _, _ ->
            shutdown()
            outputFile.delete()
        }

        return mr
    }

    private fun parseOptions(json: String?): Options {
        if (json.isNullOrBlank()) return Options()
        return try {
            val obj = JSONObject(json)
            Options(format = obj.optString("format", "aac"))
        } catch (_: Exception) {
            Options()
        }
    }

    private data class Options(val format: String = "aac")

    private fun success(data: JSONObject): String =
        JSONObject().apply {
            put("success", true)
            put("data", data)
        }.toString()

    private fun error(code: String, message: String): String =
        JSONObject().apply {
            put("success", false)
            put("code", code)
            put("message", message)
        }.toString()

    companion object {
        private val TIMESTAMP_FORMAT = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    }
}
