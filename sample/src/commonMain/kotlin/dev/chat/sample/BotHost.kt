package dev.chat.sample

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.forntoh.chat.core.Attachment
import dev.forntoh.chat.core.ConversationEvent
import dev.forntoh.chat.core.ConversationState
import dev.forntoh.chat.core.Delivery
import dev.forntoh.chat.core.Message
import dev.forntoh.chat.core.Participant
import kotlin.random.Random

class BotHost(private val seed: Int = 42) {
    val mediaHost = FakeMediaHost()
    val state: ConversationState get() = mediaHost.state
    private var random = Random(seed)
    private var elapsed = 0L
    private var running = false
    var paused by mutableStateOf(false)
        private set
    private var remainingArrivals = 0
    private var generation = 0L
    private var serial = 0L
    private var arrival = 0
    var suggestions by mutableStateOf<Map<String, TrailSuggestion>>(emptyMap())
        private set
    private val pending = mutableListOf<Scheduled>()

    init {
        receive(WELCOME)
    }

    fun start() {
        if (running) return
        running = true
        paused = false
        scheduleArrival(newBurst = true)
    }

    fun pause() {
        if (!running || paused) return
        paused = true
        generation++
        pending.clear()
        remainingArrivals = 0
    }

    fun resume() {
        if (!running) return
        if (!paused && remainingArrivals > 0) return
        paused = false
        scheduleArrival(newBurst = true)
    }

    fun reset() {
        val restart = running
        stop()
        mediaHost.clearConversation()
        random = Random(seed)
        elapsed = 0L
        serial = 0L
        arrival = 0
        suggestions = emptyMap()
        receive(WELCOME)
        if (restart) start()
    }

    fun stop() {
        pause()
        running = false
        mediaHost.cancelPendingWork()
    }

    fun advanceBy(millis: Long) {
        require(millis in 0..MAX_ADVANCE_MILLIS)
        if (!running || paused) return
        val until = elapsed + millis
        while (true) {
            val next = pending.minByOrNull { it.due }?.takeIf { it.due <= until } ?: break
            pending.remove(next)
            elapsed = next.due
            if (next.generation == generation) next.action()
        }
        elapsed = until
    }

    fun onEvent(event: ConversationEvent) {
        if (!running) return
        mediaHost.onEvent(event)
        val submission = mediaHost.submission ?: return
        mediaHost.resolveDraft(true)
        while (mediaHost.upload != null) mediaHost.completeUpload(true)
        if (!paused && remainingArrivals == 0) scheduleArrival(newBurst = true)
        if (!paused && submission.text.isNotBlank()) {
            schedule(random.nextLong(MIN_REPLY_MILLIS, MAX_REPLY_MILLIS)) {
                receive("That sounds lovely. We can keep it relaxed and leave time to explore.")
            }
        }
    }

    private fun receive(text: String, image: Boolean = false, suggestion: TrailSuggestion? = null) {
        serial++
        val id = "bot-$generation-$serial"
        val attachments = if (image) {
            listOf(
                Attachment("scout-image-$serial", "image/vector", "bundled:mountain", "Mountain morning illustration"),
            )
        } else {
            emptyList()
        }
        if (suggestion != null) suggestions = suggestions + (id to suggestion)
        mediaHost.receive(Message(id, Participant("bot", "Scout"), serial, "Now", text, Delivery.Sent, attachments))
    }

    private fun scheduleArrival(newBurst: Boolean = false) {
        if (newBurst) remainingArrivals = ARRIVAL_KINDS
        schedule(random.nextLong(MIN_ARRIVAL_MILLIS, MAX_ARRIVAL_MILLIS)) {
            when (arrival++ % ARRIVAL_KINDS) {
                0 -> receive("A small adventure can fit into an ordinary weekend. A walk, a good view, and something warm to drink.")

                1 -> receive("A little inspiration for our next walk.", image = true)

                else -> receive(
                    "Here's a trail idea for the weekend.",
                    suggestion = TrailSuggestion(
                        "The lakeside loop",
                        "Example trail · 6 km · about 90 min",
                        "An easy pace, with a picnic stop by the water.",
                    ),
                )
            }
            remainingArrivals--
            if (remainingArrivals > 0) scheduleArrival()
        }
    }

    private fun schedule(delay: Long, action: () -> Unit) {
        if (running && !paused) pending += Scheduled(elapsed + delay, generation, action)
    }

    private data class Scheduled(
        val due: Long,
        val generation: Long,
        val action: () -> Unit,
    )
}

private const val MIN_REPLY_MILLIS = 900L
private const val MAX_REPLY_MILLIS = 1_800L
private const val MAX_ADVANCE_MILLIS = 60_000L

data class TrailSuggestion(
    val title: String,
    val detail: String,
    val description: String,
)

private const val MIN_ARRIVAL_MILLIS = 15_000L
private const val MAX_ARRIVAL_MILLIS = 30_001L
private const val ARRIVAL_KINDS = 3

private const val WELCOME = "Hi, I'm Scout. Let's plan a little time outside. Where would you like to go?"
