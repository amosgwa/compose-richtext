package com.halilibo.richtext.markdown

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.halilibo.richtext.ui.RichTextScope

@Composable
public actual fun RichTextScope.HtmlBlock(content: String) {
  DisposableEffect(Unit) {
    println("Html blocks are rendered literally in Compose Desktop!")
    onDispose {  }
  }
  BasicText(content)
}
