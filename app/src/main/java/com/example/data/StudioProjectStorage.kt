package com.example.data

import android.content.Context
import android.net.Uri
import com.example.ui.studio.ProjectMediaType
import com.example.ui.studio.StudioProjectSummary
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Stores Studio project drafts and saved projects privately in app internal storage
 * (`filesDir/studio_projects`), completely separate from final exported media in `MediaRepository`.
 */
object StudioProjectStorage {
    private const val PROJECTS_DIR_NAME = "studio_projects"
    private const val PROJECTS_INDEX_FILE = "projects.json"

    private val lock = Any()

    fun getProjectsDir(context: Context): File {
        val dir = File(context.filesDir, PROJECTS_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun loadSavedProjects(context: Context): List<StudioProjectSummary> = synchronized(lock) {
        val indexFile = File(getProjectsDir(context), PROJECTS_INDEX_FILE)
        if (!indexFile.exists() || indexFile.length() == 0L) return emptyList()
        return try {
            val raw = indexFile.readText(Charsets.UTF_8)
            val arr = JSONArray(raw)
            val list = ArrayList<StudioProjectSummary>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optString("id", "").trim()
                val title = obj.optString("title", "").trim()
                if (id.isEmpty() || title.isEmpty()) continue
                val mediaTypeName = obj.optString("mediaType", ProjectMediaType.AUDIO.name)
                val mediaType = if (mediaTypeName == ProjectMediaType.VIDEO.name) {
                    ProjectMediaType.VIDEO
                } else {
                    ProjectMediaType.AUDIO
                }
                val filePath = obj.optString("filePath", "").trim().takeIf { it.isNotEmpty() }
                val sourceUriStr = obj.optString("sourceUri", "").trim().takeIf { it.isNotEmpty() }
                val parsedUri = sourceUriStr?.let {
                    try {
                        Uri.parse(it)
                    } catch (_: Throwable) {
                        null
                    }
                } ?: filePath?.let { Uri.fromFile(File(it)) }
                list.add(
                    StudioProjectSummary(
                        id = id,
                        title = title,
                        mediaType = mediaType,
                        sourceUri = parsedUri,
                        filePath = filePath,
                        durationMillis = obj.optLong("durationMillis", 0L).coerceAtLeast(0L),
                        fileSizeBytes = obj.optLong("fileSizeBytes", 0L).coerceAtLeast(0L),
                        lastModifiedMillis = obj.optLong("lastModifiedMillis", System.currentTimeMillis()),
                        statusBadge = obj.optString("statusBadge", "Saved").ifBlank { "Saved" }
                    )
                )
            }
            list.sortedByDescending { it.lastModifiedMillis }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun saveAudioProject(
        context: Context,
        project: StudioProjectSummary,
        pcm: ShortArray,
        sampleRate: Int,
        writeWav: (File, ShortArray, Int, Int) -> Unit
    ): StudioProjectSummary = synchronized(lock) {
        val dir = getProjectsDir(context)
        val safeId = project.id.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "audio_project" }
        val safeSr = sampleRate.coerceAtLeast(8000)

        var resolvedFilePath = project.filePath
        var resolvedUri = project.sourceUri
        var resolvedDurationMs = project.durationMillis
        var resolvedSizeBytes = project.fileSizeBytes

        if (pcm.isNotEmpty()) {
            val internalWav = File(dir, "$safeId.wav")
            writeWav(internalWav, pcm, safeSr, 1)
            resolvedFilePath = internalWav.absolutePath
            resolvedUri = Uri.fromFile(internalWav)
            resolvedDurationMs = ((pcm.size.toDouble() / safeSr) * 1000.0).toLong()
            resolvedSizeBytes = internalWav.length()
        }

        val saved = project.copy(
            mediaType = ProjectMediaType.AUDIO,
            sourceUri = resolvedUri,
            filePath = resolvedFilePath,
            durationMillis = resolvedDurationMs,
            fileSizeBytes = resolvedSizeBytes,
            lastModifiedMillis = System.currentTimeMillis(),
            statusBadge = "Saved"
        )
        upsertProjectInternal(context, saved)
        saved
    }

    fun saveVideoProject(
        context: Context,
        project: StudioProjectSummary,
        sourceUri: Uri?,
        durationMs: Long
    ): StudioProjectSummary = synchronized(lock) {
        val saved = project.copy(
            mediaType = ProjectMediaType.VIDEO,
            sourceUri = sourceUri ?: project.sourceUri,
            durationMillis = durationMs.takeIf { it > 0L } ?: project.durationMillis,
            lastModifiedMillis = System.currentTimeMillis(),
            statusBadge = "Saved"
        )
        upsertProjectInternal(context, saved)
        saved
    }

    private fun upsertProjectInternal(context: Context, summary: StudioProjectSummary) {
        val existing = loadSavedProjects(context).toMutableList()
        val idx = existing.indexOfFirst { it.id == summary.id }
        if (idx >= 0) {
            existing[idx] = summary
        } else {
            existing.add(0, summary)
        }
        val arr = JSONArray()
        existing.sortedByDescending { it.lastModifiedMillis }.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title)
                    put("mediaType", item.mediaType.name)
                    put("sourceUri", item.sourceUri?.toString().orEmpty())
                    put("filePath", item.filePath.orEmpty())
                    put("durationMillis", item.durationMillis)
                    put("fileSizeBytes", item.fileSizeBytes)
                    put("lastModifiedMillis", item.lastModifiedMillis)
                    put("statusBadge", item.statusBadge)
                }
            )
        }
        val indexFile = File(getProjectsDir(context), PROJECTS_INDEX_FILE)
        indexFile.writeText(arr.toString(2), Charsets.UTF_8)
    }
}
