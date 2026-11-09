package com.krlbryn.sophia.core

/**
 * Product facts. Sophia is the Android edition of the student browser that ships on Windows as Lapsus;
 * the portal and limits match Lapsus for Windows (src/shared/constants.ts).
 */
object SophiaConstants {
    const val APP_NAME = "Sophia"
    const val TAGLINE = "Browse. Learn. Understand."
    const val NAME_MEANING = "From the Greek sophía, meaning wisdom."
    const val FORMERLY = "Formerly Lapsus for Android"
    const val PUBLISHER = "KRLBRYN"
    const val DEFAULT_PORTAL_URL = "https://discipulusv2.amasystem.net/"
    const val REPO_URL = "https://github.com/kerlbryandomingo-wq/lapsus"

    /** Registrable domains treated as part of the student portal. */
    val PORTAL_DOMAINS: List<String> = listOf("amasystem.net", "amaes.edu.ph")

    const val MAX_HISTORY_ENTRIES = 5000
    const val MAX_TABS = 8
    const val MAX_AI_INPUT_CHARS = 12_000
}
