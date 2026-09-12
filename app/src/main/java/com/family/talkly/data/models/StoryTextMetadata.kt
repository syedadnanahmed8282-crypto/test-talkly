package com.family.talkly.data.models

import org.json.JSONObject

enum class StoryTextStyle(val displayName: String) {
    CLASSIC("Classic"),
    MODERN("Modern"),
    ELEGANT("Elegant"),
    STRONG("Strong"),
    TYPEWRITER("Typewriter"),
    BUBBLE("Bubble"),
    HIGHLIGHT("Highlight"),
    MINIMAL("Minimal");

    companion object {
        fun fromString(name: String?): StoryTextStyle {
            return entries.firstOrNull {
                it.name.equals(name, ignoreCase = true) || it.displayName.equals(name, ignoreCase = true)
            } ?: CLASSIC
        }
    }
}

/**
 * Metadata representation for story text overlay.
 * Serializes into backward-compatible JSON string inside textContent.
 * Old plain-text stories parse with isLegacy = true and render centered / bottom caption.
 */
data class StoryTextMetadata(
    val text: String = "",
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val style: StoryTextStyle = StoryTextStyle.CLASSIC,
    val colorHex: String = "#FFFFFF",
    val scale: Float = 1.0f,
    val rotation: Float = 0.0f,
    val isLegacy: Boolean = false
) {
    fun toSerializedString(): String {
        val json = JSONObject().apply {
            put("v", 1)
            put("text", text)
            put("x", x.toDouble())
            put("y", y.toDouble())
            put("style", style.displayName)
            put("color", colorHex)
            put("scale", scale.toDouble())
            put("rotation", rotation.toDouble())
        }
        return json.toString()
    }

    companion object {
        fun parse(raw: String?, hasPhoto: Boolean = false): StoryTextMetadata {
            if (raw.isNullOrBlank()) {
                return StoryTextMetadata(text = "", isLegacy = true)
            }
            val trimmed = raw.trim()
            if (trimmed.startsWith("{\"v\":") || (trimmed.startsWith("{") && trimmed.contains("\"text\":"))) {
                try {
                    val obj = JSONObject(trimmed)
                    val textVal = obj.optString("text", "")
                    val xVal = obj.optDouble("x", 0.5).toFloat().coerceIn(0.05f, 0.95f)
                    val yVal = obj.optDouble("y", if (hasPhoto) 0.75 else 0.5).toFloat().coerceIn(0.05f, 0.95f)
                    val styleVal = StoryTextStyle.fromString(obj.optString("style", "Classic"))
                    val colorVal = obj.optString("color", "#FFFFFF")
                    val scaleVal = obj.optDouble("scale", 1.0).toFloat().coerceIn(0.5f, 3.5f)
                    val rotationVal = obj.optDouble("rotation", 0.0).toFloat()

                    return StoryTextMetadata(
                        text = textVal,
                        x = xVal,
                        y = yVal,
                        style = styleVal,
                        colorHex = colorVal,
                        scale = scaleVal,
                        rotation = rotationVal,
                        isLegacy = false
                    )
                } catch (_: Exception) {
                    // fall through to legacy
                }
            }

            // Legacy plain text story
            return StoryTextMetadata(
                text = raw,
                x = 0.5f,
                y = if (hasPhoto) 0.82f else 0.5f,
                style = StoryTextStyle.CLASSIC,
                colorHex = "#FFFFFF",
                scale = 1.0f,
                rotation = 0.0f,
                isLegacy = true
            )
        }

        fun extractPlainText(raw: String?): String {
            if (raw.isNullOrBlank()) return ""
            val trimmed = raw.trim()
            if (trimmed.startsWith("{\"v\":") || (trimmed.startsWith("{") && trimmed.contains("\"text\":"))) {
                try {
                    val obj = JSONObject(trimmed)
                    return obj.optString("text", "")
                } catch (_: Exception) {}
            }
            return raw
        }
    }
}
