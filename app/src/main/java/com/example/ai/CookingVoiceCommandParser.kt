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
        "ten" to 10, "twelve" to 12, "fifteen" to 15, "twenty" to 20, "thirty" to 30,
        // Spanish / Portuguese
        "un" to 1, "uno" to 1, "una" to 1, "dos" to 2, "dois" to 2, "duas" to 2,
        "tres" to 3, "três" to 3, "cuatro" to 4, "quatro" to 4, "cinco" to 5,
        "seis" to 6, "siete" to 7, "sete" to 7, "ocho" to 8, "oito" to 8,
        "nueve" to 9, "nove" to 9, "diez" to 10, "dez" to 10,
        // French
        "une" to 1, "deux" to 2, "trois" to 3, "quatre" to 4, "cinq" to 5,
        "six" to 6, "sept" to 7, "huit" to 8, "neuf" to 9, "dix" to 10,
        // German
        "eins" to 1, "eine" to 1, "zwei" to 2, "drei" to 3, "vier" to 4, "fünf" to 5,
        "zehn" to 10
    )

    fun parse(rawTranscript: String): CookingVoiceCommand {
        val text = rawTranscript.trim()
        val lower = text.lowercase()

        if (lower.isBlank()) return CookingVoiceCommand.RepeatStep

        // 1. Timer duration commands: "set a 7 minute timer", "temporizador 5 minutos", "minuteur 3 minutes"
        if (lower.contains("timer") || lower.contains("countdown") || lower.contains("alarm") ||
            lower.contains("temporizador") || lower.contains("minuteur") || lower.contains("cronometro") ||
            lower.contains("cronómetro") || lower.contains("wecker")
        ) {
            val digitMinutes = Regex("(\\d+)\\s*(minute|minutes|min|mins|minuto|minutos|minuten|minuti)\\b").find(lower)?.groupValues?.get(1)?.toIntOrNull()
            val digitSeconds = Regex("(\\d+)\\s*(second|seconds|sec|secs|segundo|segundos|secondes|sekunden|secondi)\\b").find(lower)?.groupValues?.get(1)?.toIntOrNull()

            if (digitMinutes != null || digitSeconds != null) {
                val totalSeconds = ((digitMinutes ?: 0) * 60 + (digitSeconds ?: 0)).coerceIn(5, 7200)
                return CookingVoiceCommand.SetTimerSeconds(seconds = totalSeconds, autoStart = true)
            }

            // Check spoken word numbers e.g. "set a five minute timer", "cinco minutos temporizador"
            val wordMinMatch = Regex("\\b([a-z]+)\\s+(minute|minutes|min|minuto|minutos|minuten|minuti)\\b").find(lower)
            val wordMins = wordMinMatch?.groupValues?.get(1)?.let { numberWords[it] }
            if (wordMins != null) {
                return CookingVoiceCommand.SetTimerSeconds(seconds = (wordMins * 60).coerceIn(5, 7200), autoStart = true)
            }

            return when {
                lower.contains("pause") || lower.contains("stop") || lower.contains("pausa") || lower.contains("anhalten") -> CookingVoiceCommand.PauseTimer
                lower.contains("reset") || lower.contains("clear") || lower.contains("reiniciar") || lower.contains("effacer") -> CookingVoiceCommand.ResetTimer
                else -> CookingVoiceCommand.StartTimer
            }
        }

        // 2. Step navigation & speech controls (multilingual support)
        return when {
            lower.contains("next step") || lower == "next" || lower.contains("done with step") || lower.contains("continue") ||
                lower.contains("siguiente") || lower.contains("próximo") || lower.contains("proximo") || lower.contains("avanti") ||
                lower.contains("suivant") || lower.contains("weiter") || lower.contains("continuer") ->
                CookingVoiceCommand.NextStep

            lower.contains("previous") || lower.contains("last step") || lower.contains("go back") || lower == "back" ||
                lower.contains("anterior") || lower.contains("atrás") || lower.contains("atras") || lower.contains("retour") ||
                lower.contains("précédent") || lower.contains("precedente") || lower.contains("zurück") || lower.contains("zurueck") ->
                CookingVoiceCommand.PreviousStep

            lower.contains("stop reading") || lower.contains("be quiet") || lower.contains("mute") || lower == "stop" ||
                lower.contains("silencio") || lower.contains("silence") || lower.contains("parar") || lower.contains("detener") ||
                lower.contains("stopp") || lower.contains("ruhe") ->
                CookingVoiceCommand.StopSpeaking

            lower.contains("read all") || lower.contains("all steps") || lower.contains("leer todo") || lower.contains("tous les") ||
                lower.contains("tutti i passi") || lower.contains("alles lesen") ->
                CookingVoiceCommand.ReadAllSteps

            lower.contains("repeat") || lower.contains("again") || lower.contains("read step") ||
                lower.contains("repetir") || lower.contains("de nuevo") || lower.contains("otra vez") ||
                lower.contains("répéter") || lower.contains("encore") || lower.contains("ripeti") ||
                lower.contains("wiederholen") || lower.contains("nochmal") ->
                CookingVoiceCommand.RepeatStep

            else -> CookingVoiceCommand.AskSousChef(text)
        }
    }
}
