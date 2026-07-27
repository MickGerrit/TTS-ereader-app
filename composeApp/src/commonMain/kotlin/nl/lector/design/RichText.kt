package nl.lector.design

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * Snackbars and notes carry emphasis that matters: `*bold*` for the thing acted on,
 * `` `mono` `` for filenames and figures. Two markers is the whole grammar — enough
 * for the copy the design actually uses, and nothing to learn.
 */
@Composable
fun rich(text: String, monoColor: Color = LocalChrome.current.accent): AnnotatedString {
    val fonts = LocalFonts.current
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when (val ch = text[i]) {
                '*', '`' -> {
                    val end = text.indexOf(ch, i + 1)
                    if (end == -1) {
                        append(ch); i++
                    } else {
                        val inner = text.substring(i + 1, end)
                        val style = if (ch == '*') {
                            SpanStyle(fontWeight = FontWeight.SemiBold)
                        } else {
                            SpanStyle(fontFamily = fonts.mono, color = monoColor)
                        }
                        withStyle(style) { append(inner) }
                        i = end + 1
                    }
                }

                else -> { append(ch); i++ }
            }
        }
    }
}
