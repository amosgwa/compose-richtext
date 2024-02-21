package com.halilibo.richtext.ui

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow

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

  BasicText(
    text = text,
    modifier = modifier,
    style = style,
    onTextLayout = onTextLayout,
    overflow = overflow,
    softWrap = softWrap,
    maxLines = maxLines,
    inlineContent = inlineContent
  )
}
