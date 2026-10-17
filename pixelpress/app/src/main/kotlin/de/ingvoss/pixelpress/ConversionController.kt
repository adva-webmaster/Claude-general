package de.ingvoss.pixelpress

import android.app.Application
import android.content.Intent
import android.media.MediaScannerConnection
import de.ingvoss.pixelpress.core.ConversionMode
import de.ingvoss.pixelpress.core.ConversionPlanner
import de.ingvoss.pixelpress.core.FfmpegCommand
import de.ingvoss.pixelpress.core.Plausibility
import de.ingvoss.pixelpress.core.Reason
import de.ingvoss.pixelpress.core.SkipReason
import de.ingvoss.pixelpress.core.StreamInfo
import de.ingvoss.pixelpress.core.Verdict
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Hält den Stand der Oberfläche und führt die Umwandlung aus. Lebt so lange wie der Prozess (Application); ein
 * Vordergrunddienst ([ConversionService]) sorgt dafür, dass Android den Prozess während der Arbeit nicht beendet.
 */
class ConversionController(private val app: Application) {
    private val settings = Settings(app)
    private val runtime = FfmpegRuntime(app)
    private val tool = FfmpegTool(runtime)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var runJob: Job? = null

    private val _state = MutableStateFlow(UiState(directory = settings.directory, options = settings.loadOptions()))
    val state: StateFlow<UiState> = _state.asStateFlow()

    // ---- Verzeichnis und Einstellungen ------------------------------------------------------

    fun setDirectory(path: String) {
        settings.directory = path
        _state.update { it.copy(directory = path, error = null) }
        scan()
    }

    fun updateOptions(change: (Options) -> Options) {
        val before = _state.value.options
        val after = change(before)
        settings.saveOptions(after)
        _state.update { it.copy(options = after) }
        // Wer nur die Suche verändert, bekommt eine neue Liste; die übrigen Einstellungen betreffen erst den Durchlauf.
        if ((after.recursive != before.recursive || after.skipExisting != before.skipExisting) && !_state.value.running) scan()
    }

    /** Übersprungene und schon umgewandelte Zeilen lassen sich nicht (erneut) auswählen. */
    private fun ItemState.selectable() = this != ItemState.SKIPPED && this != ItemState.DONE

    fun toggleSelected(index: Int) = updateItem(index) { if (it.state.selectable()) it.copy(selected = !it.selected) else it }

    fun selectAll(selected: Boolean) {
        _state.update { st -> st.copy(items = st.items.map { if (it.state.selectable()) it.copy(selected = selected) else it }) }
    }

    /** Durchsucht das gewählte Verzeichnis neu. */
    fun scan() {
        val st = _state.value
        val directory = st.directory ?: return
        if (st.running) return
        _state.update { it.copy(scanning = true, items = emptyList(), finished = false, offerDeletion = false) }
        scope.launch {
            val options = _state.value.options
            val found = Scanner.scan(File(directory), options.recursive)
            val planned = ConversionPlanner.plan(found.sources, found.existingMp4, options.skipExisting)
            val items = planned.map { job ->
                val skipped = job.skip != null
                JobItem(
                    job = job,
                    selected = !skipped,
                    state = if (skipped) ItemState.SKIPPED else ItemState.WAITING,
                    message = if (job.skip == SkipReason.OUTPUT_EXISTS) app.getString(R.string.item_skipped_exists) else null
                )
            }
            _state.update { it.copy(scanning = false, items = items) }
        }
    }

    // ---- Durchlauf --------------------------------------------------------------------------

    fun start() {
        val st = _state.value
        if (st.running) return
        val todo = st.items.indices.filter { st.items[it].selected && st.items[it].state.selectable() }
        if (todo.isEmpty()) return
        _state.update { current ->
            current.copy(
                running = true, finished = false, offerDeletion = false, error = null,
                items = current.items.mapIndexed { i, item ->
                    if (i in todo) item.copy(state = ItemState.WAITING, progress = null, verdict = null, message = null, mode = null, outputBytes = 0) else item
                }
            )
        }
        app.startForegroundService(Intent(app, ConversionService::class.java))
        runJob = scope.launch { runAll(todo) }
    }

    fun cancel() {
        runJob?.cancel()
    }

    private suspend fun runAll(indices: List<Int>) {
        try {
            _state.update { it.copy(preparing = true) }
            val problem = runtime.prepare()
            _state.update { it.copy(preparing = false) }
            if (problem != null) {
                _state.update { it.copy(error = problem) }
                return
            }
            for (index in indices) {
                currentCoroutineContext().ensureActive()
                process(index)
            }
        } finally {
            val offer = _state.value.options.deleteOriginals && _state.value.items.any { it.verified && !it.originalDeleted }
            _state.update { it.copy(running = false, preparing = false, finished = true, offerDeletion = offer) }
        }
    }

    private class Outcome(val ok: Boolean, val mode: ConversionMode, val verdict: Verdict?, val message: String?, val outputBytes: Long)

    private suspend fun process(index: Int) {
        val item = _state.value.items[index]
        val source = item.job.source
        val input = File(source.path)
        updateItem(index) { it.copy(state = ItemState.RUNNING, progress = null, message = null) }
        try {
            if (!input.exists()) {
                updateItem(index) { it.copy(state = ItemState.FAILED, message = app.getString(R.string.item_error_missing)) }
                return
            }
            val probe = tool.probe(source.path)
            val info = probe.info
            if (probe.error != null) {
                updateItem(index) { it.copy(state = ItemState.FAILED, message = app.getString(R.string.item_error_probe, probe.error)) }
                return
            }
            if (info.videoCodec == null) {
                updateItem(index) { it.copy(state = ItemState.FAILED, message = app.getString(R.string.item_error_no_video)) }
                return
            }
            val options = _state.value.options
            val copy = options.copyWhenPossible && FfmpegCommand.canCopy(info)
            var outcome = convertOnce(index, info, if (copy) ConversionMode.COPY else ConversionMode.TRANSCODE)
            // Umpacken kann an Eigenheiten der Datei scheitern: dann einmal mit Neuberechnung versuchen.
            if (!outcome.ok && copy) outcome = convertOnce(index, info, ConversionMode.TRANSCODE)
            updateItem(index) {
                it.copy(
                    state = if (outcome.ok) ItemState.DONE else ItemState.FAILED,
                    progress = if (outcome.ok) 1f else null,
                    mode = outcome.mode, verdict = outcome.verdict, message = outcome.message, outputBytes = outcome.outputBytes
                )
            }
        } catch (e: CancellationException) {
            withContext(NonCancellable) { updateItem(index) { it.copy(state = ItemState.CANCELLED, progress = null) } }
            throw e
        }
    }

    private suspend fun convertOnce(index: Int, info: StreamInfo, mode: ConversionMode): Outcome {
        val item = _state.value.items[index]
        val source = item.job.source
        val output = File(item.job.outputPath)
        val part = File(item.job.outputPath + ".part")
        val quality = _state.value.options.quality
        updateItem(index) { it.copy(mode = mode, progress = null) }
        if (output.exists()) return Outcome(false, mode, null, app.getString(R.string.item_error_output_exists), 0)
        part.delete()
        var finished = false
        try {
            val result = tool.convert(source.path, part.path, mode, quality, info) { fraction, _ ->
                updateItem(index) { it.copy(progress = fraction) }
            }
            if (result is ConvertResult.Failed) return Outcome(false, mode, null, result.message, 0)
            val outputBytes = part.length()
            val outInfo = tool.probe(part.path).info
            val inputBytes = File(source.path).length()
            val verdict = Plausibility.check(mode, inputBytes, outputBytes, info.durationMs, outInfo.durationMs)
            if (!verdict.ok) return Outcome(false, mode, verdict, reasonText(verdict), outputBytes)
            if (!part.renameTo(output)) return Outcome(false, mode, verdict, app.getString(R.string.item_error_rename), outputBytes)
            finished = true
            MediaScannerConnection.scanFile(app, arrayOf(output.path), null, null)
            return Outcome(true, mode, verdict, null, outputBytes)
        } finally {
            if (!finished) withContext(NonCancellable) { part.delete() }
        }
    }

    private fun reasonText(verdict: Verdict): String = app.getString(
        when (verdict.reason) {
            Reason.OK -> R.string.reason_ok
            Reason.INPUT_UNREADABLE -> R.string.reason_input_unreadable
            Reason.OUTPUT_MISSING_OR_EMPTY -> R.string.reason_output_missing
            Reason.OUTPUT_TOO_SMALL -> R.string.reason_too_small
            Reason.OUTPUT_TOO_LARGE -> R.string.reason_too_large
            Reason.DURATION_MISMATCH -> R.string.reason_duration
        }
    )

    // ---- Originale löschen ------------------------------------------------------------------

    fun dismissDeletionOffer() {
        _state.update { it.copy(offerDeletion = false) }
    }

    /**
     * Löscht die Originale aller bestätigten Aufträge. Vorher wird noch einmal geprüft, dass die MP4 noch da ist und
     * genauso groß wie nach dem Umwandeln.
     */
    fun deleteOriginals() {
        if (_state.value.running) return
        _state.update { it.copy(offerDeletion = false) }
        scope.launch {
            val items = _state.value.items
            val deleted = ArrayList<String>()
            for (index in items.indices) {
                val item = items[index]
                if (!item.verified || item.originalDeleted) continue
                val output = File(item.job.outputPath)
                val source = File(item.job.source.path)
                val safe = output.exists() && output.length() == item.outputBytes && output.length() >= Plausibility.MIN_OUTPUT_BYTES
                when {
                    !safe -> updateItem(index) { it.copy(message = app.getString(R.string.item_delete_unsafe)) }
                    !source.exists() || source.delete() -> {
                        deleted += source.path
                        updateItem(index) { it.copy(originalDeleted = true, message = null) }
                    }
                    else -> updateItem(index) { it.copy(message = app.getString(R.string.item_delete_failed)) }
                }
            }
            if (deleted.isNotEmpty()) MediaScannerConnection.scanFile(app, deleted.toTypedArray(), null, null)
        }
    }

    private fun updateItem(index: Int, change: (JobItem) -> JobItem) {
        _state.update { st ->
            if (index !in st.items.indices) st else st.copy(items = st.items.toMutableList().also { it[index] = change(it[index]) })
        }
    }
}
