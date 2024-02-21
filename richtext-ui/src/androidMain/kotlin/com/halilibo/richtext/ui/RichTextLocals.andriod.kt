package com.halilibo.richtext.ui

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import com.halilibo.richtext.ui.string.RichTextString.Format.Code
import me.saket.extendedspans.ExtendedSpans
import me.saket.extendedspans.RoundedCornerSpanPainter
import me.saket.extendedspans.RoundedCornerSpanPainter.Stroke
import me.saket.extendedspans.RoundedCornerSpanPainter.TextPaddingValues
import me.saket.extendedspans.drawBehind

@Composable
public actual fun RichTextScope.Text(
  text: AnnotatedString,
  modifier: Modifier,
  onTextLayout: (TextLayoutResult) -> Unit,
  overflow: TextOverflow,
  softWrap: Boolean,
  maxLines: Int,
  inlineContent: Map<String, InlineTextContent>,
) {
  val textColor = currentTextStyle.color.takeOrElse { currentContentColor }
  val style = currentTextStyle.copy(color = textColor)
  val currentInlineCodeStyle =
    currentRichTextStyle.stringStyle?.codeStyle ?: Code.DefaultInlineCodeStyle

  // TODO Set style by passing through the [RichTextStringStyle].
  val extendedSpans = remember {
    ExtendedSpans(
      RoundedCornerSpanPainter(
        cornerRadius = currentInlineCodeStyle.cornerRadius,
        padding = TextPaddingValues(
          horizontal = currentInlineCodeStyle.horizontalPadding,
          vertical = currentInlineCodeStyle.verticalPadding
        ),
        topMargin = currentInlineCodeStyle.topMargin,
        bottomMargin = currentInlineCodeStyle.bottomMargin,
        stroke = Stroke(
          color = currentInlineCodeStyle.borderStrokeColor,
          width = currentInlineCodeStyle.borderStrokeWidth
        ),
      ),
    )
  }

  BasicText(
    text = remember(text) {
      extendedSpans.extend(text)
    },
    modifier = modifier.drawBehind(extendedSpans),
    style = style,
    onTextLayout = {
      extendedSpans.onTextLayout(it)
      onTextLayout(it)
    },
    overflow = overflow,
    softWrap = softWrap,
    maxLines = maxLines,
    inlineContent = inlineContent
  )
}
