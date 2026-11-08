package hey.skipad.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicLong

enum class SkipOutcome { CLICKED, GESTURE, FAILED, BLOCKED }

data class SkipLogEntry(
    val id: Long,
    val timeMillis: Long,
    val packageName: String,
    val label: String,
    val rule: String,
    val outcome: SkipOutcome,
)

/**
 * Ring buffer of the most recent skip attempts, persisted to disk so that the
 * log survives service restarts (the counters are persisted separately, which
 * used to leave "stats without logs").
 */
object LogStore {
    private const val MAX_ENTRIES = 200
    private const val FILE_NAME = "skip_logs.json"

    private val seq = AtomicLong(0)
    private val _entries = MutableStateFlow<List<SkipLogEntry>>(emptyList())
    val entries: StateFlow<List<SkipLogEntry>> = _entries.asStateFlow()

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var saveScope: CoroutineScope? = null

    @Volatile
    private var saveJob: Job? = null

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        load()
    }

    fun add(packageName: String, label: String, rule: String, outcome: SkipOutcome) {
        val entry = SkipLogEntry(
            id = seq.incrementAndGet(),
            timeMillis = System.currentTimeMillis(),
            packageName = packageName,
            label = label,
            rule = rule,
            outcome = outcome,
        )
        _entries.update { old -> (listOf(entry) + old).take(MAX_ENTRIES) }
        scheduleSave()
    }

    fun clear() {
        _entries.value = emptyList()
        scheduleSave()
    }

    private fun load() {
        val context = appContext ?: return
        runCatching {
            val file = File(context.filesDir, FILE_NAME)
            if (!file.exists()) return
            val array = JSONArray(file.readText())
            val loaded = ArrayList<SkipLogEntry>(array.length())
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                loaded.add(
                    SkipLogEntry(
                        id = item.optLong("id"),
                        timeMillis = item.optLong("time"),
                        packageName = item.optString("pkg"),
                        label = item.optString("label"),
                        rule = item.optString("rule"),
                        outcome = runCatching {
                            SkipOutcome.valueOf(item.optString("outcome"))
                        }.getOrDefault(SkipOutcome.CLICKED),
                    ),
                )
            }
            val ordered = loaded.sortedByDescending { it.timeMillis }.take(MAX_ENTRIES)
            _entries.value = ordered
            seq.set((ordered.maxOfOrNull { it.id } ?: 0L) + 1)
        }
    }

    private fun scheduleSave() {
        val scope = saveScope ?: return
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(250)
            persist()
        }
    }

    private fun persist() {
        val context = appContext ?: return
        runCatching {
            val array = JSONArray()
            _entries.value.forEach { entry ->
                array.put(
                    JSONObject().apply {
                        put("id", entry.id)
                        put("time", entry.timeMillis)
                        put("pkg", entry.packageName)
                        put("label", entry.label)
                        put("rule", entry.rule)
                        put("outcome", entry.outcome.name)
                    },
                )
            }
            File(context.filesDir, FILE_NAME).writeText(array.toString())
        }
    }
}
