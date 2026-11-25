package com.example.ai

/**
 * Structured hands-free voice actions for Interactive Cooking Mode (0 API cost).
 */
sealed interface CookingVoiceCommand {
    data object NextStep : CookingVoiceCommand
    data object PreviousStep : CookingVoiceCommand
    data object RepeatStep : CookingVoiceCommand
    data object ReadAllSteps : CookingVoiceCommand
    data object StopSpeaking : CookingVoiceCommand
    data class SetTimerSeconds(val seconds: Int, val autoStart: Boolean = true) : CookingVoiceCommand
    data object StartTimer : CookingVoiceCommand
    data object PauseTimer : CookingVoiceCommand
    data object ResetTimer : CookingVoiceCommand
    data class AskSousChef(val question: String) : CookingVoiceCommand
}

/**
 * Deterministic, zero-latency on-device voice command parser for messy-hands stove cooking.
 * Handles commands like:
 * - "Next step", "Done", "Previous step", "Repeat"
 * - "Set a 7 minute timer", "Timer for 90 seconds", "Pause timer", "Start timer"
 * - "What can I swap for heavy cream?" -> routes to Sous-Chef Chat
 */
object CookingVoiceCommandParser {

    private val numberWords = mapOf(
        "one" to 1, "a" to 1, "an" to 1, "two" to 2, "three" to 3, "four" to 4,
        "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9,
        "ten" to 10, "twelve" to 12, "fifteen" to 15, "twenty" to 20, "thirty" to 30
    )

    fun parse(rawTranscript: String): CookingVoiceCommand {
        val text = rawTranscript.trim()
        val lower = text.lowercase()

        if (lower.isBlank()) return CookingVoiceCommand.RepeatStep

        // 1. Timer duration commands: "set a 7 minute timer", "timer 5 min", "45 seconds timer"
        if (lower.contains("timer") || lower.contains("countdown") || lower.contains("alarm")) {
            val digitMinutes = Regex("(\\d+)\\s*(minute|minutes|min|mins)\\b").find(lower)?.groupValues?.get(1)?.toIntOrNull()
            val digitSeconds = Regex("(\\d+)\\s*(second|seconds|sec|secs)\\b").find(lower)?.groupValues?.get(1)?.toIntOrNull()

            if (digitMinutes != null || digitSeconds != null) {
                val totalSeconds = ((digitMinutes ?: 0) * 60 + (digitSeconds ?: 0)).coerceIn(5, 7200)
                return CookingVoiceCommand.SetTimerSeconds(seconds = totalSeconds, autoStart = true)
            }

            // Check spoken word numbers e.g. "set a five minute timer"
            val wordMinMatch = Regex("\\b([a-z]+)\\s+(minute|minutes|min)\\b").find(lower)
            val wordMins = wordMinMatch?.groupValues?.get(1)?.let { numberWords[it] }
            if (wordMins != null) {
                return CookingVoiceCommand.SetTimerSeconds(seconds = (wordMins * 60).coerceIn(5, 7200), autoStart = true)
            }

            return when {
                lower.contains("pause") || lower.contains("stop") -> CookingVoiceCommand.PauseTimer
                lower.contains("reset") || lower.contains("clear") -> CookingVoiceCommand.ResetTimer
                else -> CookingVoiceCommand.StartTimer
            }
        }

        // 2. Step navigation & speech controls
        return when {
            lower.contains("next step") || lower == "next" || lower.contains("done with step") || lower.contains("continue") ->
                CookingVoiceCommand.NextStep
            lower.contains("previous") || lower.contains("last step") || lower.contains("go back") || lower == "back" ->
                CookingVoiceCommand.PreviousStep
            lower.contains("stop reading") || lower.contains("be quiet") || lower.contains("mute") || lower == "stop" ->
                CookingVoiceCommand.StopSpeaking
            lower.contains("read all") || lower.contains("all steps") ->
                CookingVoiceCommand.ReadAllSteps
            lower.contains("repeat") || lower.contains("again") || lower.contains("read step") ->
                CookingVoiceCommand.RepeatStep
            else -> CookingVoiceCommand.AskSousChef(text)
        }
    }
}
