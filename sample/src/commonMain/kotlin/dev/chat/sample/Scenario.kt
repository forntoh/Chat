package dev.chat.sample

/** Fixture outcomes for the sample host; no scenario represents real service reliability. */
enum class Scenario(val label: String) {
    Exchange("Normal exchange"),
    Refusal("Refuse next submission"),
    Failure("Fail next delivery")
}
