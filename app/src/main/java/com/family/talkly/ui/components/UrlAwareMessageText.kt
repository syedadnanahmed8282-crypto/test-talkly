package com.family.talkly.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.TextUnit

private val URL_REGEX = Regex("""https?://[^\s<>"]+""", RegexOption.IGNORE_CASE)
private val TRAILING_PUNCTUATION = setOf('.', ',', '!', '?', ';', ':', '।', '"', '\'', '`', '…')

data class TextSegment(
    val text: String,
    val isUrl: Boolean,
    val url: String = ""
)

fun parseUrlSegments(fullText: String): List<TextSegment> {
    if (fullText.isEmpty()) return emptyList()

    if (!fullText.contains("http://", ignoreCase = true) &&
        !fullText.contains("https://", ignoreCase = true)
    ) {
        return listOf(TextSegment(text = fullText, isUrl = false))
    }

    val segments = mutableListOf<TextSegment>()
    var currentIndex = 0
    val matches = URL_REGEX.findAll(fullText)

    for (match in matches) {
        val matchStart = match.range.first
        val rawMatchedText = match.value

        if (matchStart > currentIndex) {
            segments.add(TextSegment(text = fullText.substring(currentIndex, matchStart), isUrl = false))
        }

        var trimmedLength = rawMatchedText.length
        while (trimmedLength > 0) {
            val lastChar = rawMatchedText[trimmedLength - 1]
            val currentSub = rawMatchedText.substring(0, trimmedLength)
            if (lastChar in TRAILING_PUNCTUATION) {
                trimmedLength--
            } else if (lastChar == ')' && currentSub.count { it == '(' } < currentSub.count { it == ')' }) {
                trimmedLength--
            } else if (lastChar == ']' && currentSub.count { it == '[' } < currentSub.count { it == ']' }) {
                trimmedLength--
            } else if (lastChar == '}' && currentSub.count { it == '{' } < currentSub.count { it == '}' }) {
                trimmedLength--
            } else {
                break
            }
        }

        val cleanedUrl = rawMatchedText.substring(0, trimmedLength)
        val trailingPunct = rawMatchedText.substring(trimmedLength)

        if (cleanedUrl.isNotEmpty()) {
            segments.add(TextSegment(text = cleanedUrl, isUrl = true, url = cleanedUrl))
        }
        if (trailingPunct.isNotEmpty()) {
            segments.add(TextSegment(text = trailingPunct, isUrl = false))
        }

        currentIndex = match.range.last + 1
    }

    if (currentIndex < fullText.length) {
        segments.add(TextSegment(text = fullText.substring(currentIndex), isUrl = false))
    }

    return segments
}

fun openUrlSafely(context: Context, urlString: String) {
    try {
        val trimmed = urlString.trim()
        val validUrl = if (!trimmed.startsWith("http://", ignoreCase = true) &&
            !trimmed.startsWith("https://", ignoreCase = true)
        ) {
            "https://$trimmed"
        } else {
            trimmed
        }
        val uri = Uri.parse(validUrl)
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (t: Throwable) {
        Log.e("UrlAwareMessageText", "Failed to open URL: $urlString", t)
        try {
            Toast.makeText(context, "Cannot open link: $urlString", Toast.LENGTH_SHORT).show()
        } catch (_: Throwable) {}
    }
}

/**
 * Renders chat message text with automatic detection of http:// and https:// URLs.
 * Detected URLs are made clickable with cyan color and underline, opening in the
 * system browser/URI handler on tap, while normal surrounding text remains unchanged.
 */
@Composable
fun UrlAwareMessageText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    linkColor: Color = Color(0xFF22D3EE), // TalklyCyan
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    onLinkClick: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val segments = remember(text) { parseUrlSegments(text) }
    val hasUrls = remember(segments) { segments.any { it.isUrl } }

    if (!hasUrls) {
        Text(
            text = text,
            modifier = modifier,
            style = style,
            color = color,
            fontSize = fontSize,
            maxLines = maxLines,
            overflow = overflow
        )
    } else {
        val annotatedString = remember(text, linkColor, onLinkClick) {
            buildAnnotatedString {
                for (segment in segments) {
                    if (segment.isUrl) {
                        val link = LinkAnnotation.Url(
                            url = segment.url,
                            styles = TextLinkStyles(
                                style = SpanStyle(
                                    color = linkColor,
                                    textDecoration = TextDecoration.Underline
                                )
                            ),
                            linkInteractionListener = { annotation ->
                                val clickedUrl = (annotation as? LinkAnnotation.Url)?.url ?: segment.url
                                if (onLinkClick != null) {
                                    onLinkClick(clickedUrl)
                                } else {
                                    openUrlSafely(context, clickedUrl)
                                }
                            }
                        )
                        withLink(link) {
                            append(segment.text)
                        }
                    } else {
                        append(segment.text)
                    }
                }
            }
        }

        Text(
            text = annotatedString,
            modifier = modifier,
            style = style,
            color = color,
            fontSize = fontSize,
            maxLines = maxLines,
            overflow = overflow
        )
    }
}
