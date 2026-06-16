package com.halilibo.richtext.markdown

import com.halilibo.richtext.commonmark.CommonMarkdownParseOptions
import com.halilibo.richtext.commonmark.CommonmarkAstNodeParser
import com.halilibo.richtext.commonmark.FailurePolicy
import com.halilibo.richtext.commonmark.convert
import com.halilibo.richtext.commonmark.sanitizePartialMarkdown
import com.halilibo.richtext.markdown.node.AstBlockQuote
import com.halilibo.richtext.markdown.node.AstDocument
import com.halilibo.richtext.markdown.node.AstHeading
import com.halilibo.richtext.markdown.node.AstImage
import com.halilibo.richtext.markdown.node.AstNode
import com.halilibo.richtext.markdown.node.AstNodeLinks
import com.halilibo.richtext.markdown.node.AstParagraph
import com.halilibo.richtext.markdown.node.AstText
import org.commonmark.node.Document
import org.commonmark.node.Image
import org.commonmark.node.Paragraph
import org.commonmark.node.Text
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

internal class AstNodeConvertKtTest {

  private val parser = CommonmarkAstNodeParser(CommonMarkdownParseOptions.Default)

  @Test
  fun `when image without title is converted, then the content description is empty`() {
    val destination = "/url"
    val image = Image(destination, null)

    val result = convert(image)

    assertEquals(
      expected = AstNode(
        type = AstImage(title = "", destination = destination),
        links = AstNodeLinks()
      ),
      actual = result
    )
  }

  @Test
  fun `tree links are correctly wired for a document with siblings`() {
    val root = parser.parse("# Heading\n\nParagraph text")

    // Root should be a Document
    assertEquals(AstDocument, root.type)
    assertNull(root.links.parent)

    // First child should be the heading
    val heading = root.links.firstChild
    assertNotNull(heading)
    assertEquals(AstHeading(level = 1), heading.type)
    assertSame(root, heading.links.parent)
    assertNull(heading.links.previous)

    // Second child should be the paragraph
    val paragraph = heading.links.next
    assertNotNull(paragraph)
    assertEquals(AstParagraph, paragraph.type)
    assertSame(root, paragraph.links.parent)
    assertSame(heading, paragraph.links.previous)
    assertNull(paragraph.links.next)

    // lastChild should point to the paragraph
    assertSame(paragraph, root.links.lastChild)
  }

  @Test
  fun `tree links are correctly wired for nested structures`() {
    val root = parser.parse("> quoted text")

    val blockquote = root.links.firstChild
    assertNotNull(blockquote)
    assertEquals(AstBlockQuote, blockquote.type)
    assertSame(root, blockquote.links.parent)

    val paragraph = blockquote.links.firstChild
    assertNotNull(paragraph)
    assertEquals(AstParagraph, paragraph.type)
    assertSame(blockquote, paragraph.links.parent)

    val text = paragraph.links.firstChild
    assertNotNull(text)
    assertEquals(AstText(literal = "quoted text"), text.type)
    assertSame(paragraph, text.links.parent)
  }

  @Test
  fun `document with many sibling paragraphs does not overflow`() {
    // 2000 paragraphs would cause ~2000 frames of sibling recursion
    val markdown = (1..2000).joinToString("\n\n") { "Paragraph $it" }
    val root = parser.parse(markdown)

    assertEquals(AstDocument, root.type)

    // Walk the sibling chain and count paragraphs
    var count = 0
    var node = root.links.firstChild
    while (node != null) {
      assertEquals(AstParagraph, node.type)
      count++
      node = node.links.next
    }
    assertEquals(2000, count)

    // lastChild should be the final paragraph
    assertNotNull(root.links.lastChild)
    assertNull(root.links.lastChild!!.links.next)
  }

  @Test
  fun `deeply nested blockquotes do not overflow`() {
    // 500 levels of nested blockquotes would cause ~500 frames of child recursion
    val markdown = ">".repeat(500) + " deep text"
    val root = parser.parse(markdown)

    assertEquals(AstDocument, root.type)

    // Walk down the child chain counting blockquotes
    var depth = 0
    var node = root.links.firstChild
    while (node != null && node.type is AstBlockQuote) {
      depth++
      node = node.links.firstChild
    }
    assertEquals(500, depth)

    // The innermost blockquote should contain a paragraph with text
    assertNotNull(node)
    assertEquals(AstParagraph, node.type)
  }

  /**
   * Proves that the sibling chain depth we test would overflow a recursive implementation
   * on a constrained thread stack (similar to Android's ~1MB default), while our iterative
   * convert() handles it without issue.
   */
  @Test
  fun `convert handles long sibling chains that would overflow a recursive implementation`() {
    val siblingCount = 5000
    // Build a CommonMark tree directly: Document -> Paragraph("1") -> Paragraph("2") -> ...
    val doc = Document()
    for (i in 1..siblingCount) {
      val para = Paragraph()
      para.appendChild(Text("$i"))
      doc.appendChild(para)
    }

    val stackSize = 256L * 1024 // 256KB — smaller than Android's default ~1MB

    // First, prove this stack size is too small for equivalent-depth recursion.
    // A simple recursive chain of siblingCount depth will overflow.
    val recursionOverflowed = AtomicReference<Boolean>(false)
    val recursionThread = Thread(null, {
      try {
        countRecursively(siblingCount)
      } catch (_: StackOverflowError) {
        recursionOverflowed.set(true)
      }
    }, "recursion-test", stackSize)
    recursionThread.start()
    recursionThread.join()
    assertTrue(
      recursionOverflowed.get(),
      "Expected StackOverflowError for $siblingCount recursive calls on ${stackSize / 1024}KB stack"
    )

    // Now prove our iterative convert() handles the same depth on the same stack size.
    val convertError = AtomicReference<Throwable?>(null)
    val convertResult = AtomicReference<AstNode?>(null)
    val convertThread = Thread(null, {
      try {
        convertResult.set(convert(doc))
      } catch (e: Throwable) {
        convertError.set(e)
      }
    }, "convert-test", stackSize)
    convertThread.start()
    convertThread.join()

    val error = convertError.get()
    if (error != null) {
      fail("convert() should not throw on a long sibling chain, but threw: $error")
    }

    val root = convertResult.get()
    assertNotNull(root, "convert() should return a non-null root")
    assertEquals(AstDocument, root.type)

    // Verify the full sibling chain was converted
    var count = 0
    var node = root.links.firstChild
    while (node != null) {
      count++
      node = node.links.next
    }
    assertEquals(siblingCount, count)
  }
  // region sanitizePartialMarkdown — incomplete link stripping

  @Test
  fun `sanitizer strips incomplete link with partial url`() {
    val input = "Check out [this link](https://exam"
    val result = sanitizePartialMarkdown(input)
    assertEquals("Check out this link", result)
  }

  @Test
  fun `sanitizer strips incomplete link with no url yet`() {
    val input = "Check out [this link]("
    val result = sanitizePartialMarkdown(input)
    assertEquals("Check out this link", result)
  }

  @Test
  fun `sanitizer strips unclosed bracket`() {
    val input = "Check out [this li"
    val result = sanitizePartialMarkdown(input)
    assertEquals("Check out this li", result)
  }

  @Test
  fun `sanitizer strips incomplete image link`() {
    val input = "See ![alt text](https://example.com/img"
    val result = sanitizePartialMarkdown(input)
    assertEquals("See alt text", result)
  }

  @Test
  fun `sanitizer preserves complete links`() {
    val input = "Check out [this link](https://example.com) for more"
    val result = sanitizePartialMarkdown(input)
    assertEquals(input, result)
  }

  @Test
  fun `sanitizer preserves complete image links`() {
    val input = "See ![alt](https://example.com/img.png) here"
    val result = sanitizePartialMarkdown(input)
    assertEquals(input, result)
  }

  @Test
  fun `partial parser does not produce clickable link from truncated url`() {
    val partialParser = CommonmarkAstNodeParser(CommonMarkdownParseOptions.Streaming)
    val result = partialParser.parse("Click [here](https://partial")
    assertEquals(AstDocument, result.type)
    // The paragraph should contain plain text, not a Link node
    val paragraph = result.links.firstChild
    assertNotNull(paragraph)
    assertEquals(AstParagraph, paragraph.type)
    val textNode = paragraph.links.firstChild
    assertNotNull(textNode)
    assertTrue(textNode.type is AstText, "Expected plain text, got: ${textNode.type}")
  }

  // endregion

  @Test
  fun `sanitizer strips trailing backslash`() {
    val input = "Hello world\\"
    val result = sanitizePartialMarkdown(input)
    assertEquals("Hello world", result)
  }

  @Test
  fun `sanitizer preserves escaped characters`() {
    val input = "Hello \\* world"
    val result = sanitizePartialMarkdown(input)
    assertEquals(input, result)
  }

  @Test
  fun `sanitizer strips trailing backslash before closing delimiters`() {
    val input = "This is *italic\\"
    val result = sanitizePartialMarkdown(input)
    assertTrue(result.endsWith("*"), "Expected closing *, got: $result")
    assertTrue("italic" in result, "Expected italic text preserved, got: $result")
  }

  @Test
  fun `sanitizer closes nested bold and italic`() {
    val input = "**bold *and italic"
    val result = sanitizePartialMarkdown(input)
    // bold ** is unclosed (1 occurrence, odd) → append **
    // italic * is unclosed (1 single *, odd) → append *
    // Order: ** then * (bold is appended before italic in the code)
    assertEquals("**bold *and italic***", result)
  }

  @Test
  fun `sanitizer closes bold-italic triple asterisk`() {
    val input = "***bold-italic text"
    val result = sanitizePartialMarkdown(input)
    // *** = one ** (bold) + one single * (italic), both unclosed
    assertEquals("***bold-italic text***", result)
  }

  @Test
  fun `sanitizer respects escaped delimiter before real one`() {
    // The \* is escaped (not italic), but the second * opens italic
    val input = "This is \\*not italic *but this is"
    val result = sanitizePartialMarkdown(input)
    // Only the unescaped * should be counted and closed
    assertEquals("This is \\*not italic *but this is*", result)
  }

  @Test
  fun `sanitizer closes unclosed inline code and leaves enclosed asterisks literal`() {
    val input = "Some `code and **bold"
    val result = sanitizePartialMarkdown(input)
    // The ** is inside the (unclosed) code span, so it is literal — only the
    // backtick is closed. Appending a ** as well would leave a stray ** after
    // the code span.
    assertEquals("Some `code and **bold`", result)
  }

  // region sanitizePartialMarkdown — inline delimiter tests

  @Test
  fun `sanitizer closes unclosed fenced code block`() {
    val input = "Hello\n```kotlin\nval x = 1"
    val result = sanitizePartialMarkdown(input)
    assertTrue(result.endsWith("```"), "Expected closing fence, got: $result")
  }

  @Test
  fun `sanitizer closes unclosed tilde fenced code block`() {
    val input = "Hello\n~~~\nsome code"
    val result = sanitizePartialMarkdown(input)
    assertTrue(result.endsWith("~~~"), "Expected closing fence, got: $result")
  }

  @Test
  fun `sanitizer does not modify already closed fenced code block`() {
    val input = "```\ncode\n```"
    val result = sanitizePartialMarkdown(input)
    assertEquals(input, result)
  }

  @Test
  fun `sanitizer closes unclosed bold delimiter`() {
    val input = "This is **bold text"
    val result = sanitizePartialMarkdown(input)
    assertTrue(result.endsWith("**"), "Expected closing **, got: $result")
  }

  @Test
  fun `sanitizer closes unclosed italic delimiter`() {
    val input = "This is *italic text"
    val result = sanitizePartialMarkdown(input)
    assertTrue(result.endsWith("*"), "Expected closing *, got: $result")
  }

  @Test
  fun `sanitizer closes unclosed inline code`() {
    val input = "This is `inline code"
    val result = sanitizePartialMarkdown(input)
    assertTrue(result.endsWith("`"), "Expected closing backtick, got: $result")
  }

  @Test
  fun `sanitizer closes unclosed strikethrough`() {
    val input = "This is ~~struck"
    val result = sanitizePartialMarkdown(input)
    assertTrue(result.endsWith("~~"), "Expected closing ~~, got: $result")
  }

  @Test
  fun `sanitizer does not modify complete markdown`() {
    val input = "This is **bold** and *italic* and `code` and ~~struck~~"
    val result = sanitizePartialMarkdown(input)
    assertEquals(input, result)
  }

  // endregion

  // region sanitizePartialMarkdown — bare opener stripping
  // A delimiter run that has just streamed in with no content after it must be
  // dropped, not closed: closing it produces an empty span (e.g. "The **" ->
  // "The ****") that CommonMark renders as the literal **/**** garble.

  @Test
  fun `sanitizer strips trailing bare bold opener instead of creating empty span`() {
    val input = "The space between **"
    val result = sanitizePartialMarkdown(input)
    assertEquals("The space between ", result)
  }

  @Test
  fun `sanitizer strips trailing bare italic opener`() {
    val input = "Hello *"
    val result = sanitizePartialMarkdown(input)
    assertEquals("Hello ", result)
  }

  @Test
  fun `sanitizer strips trailing bare inline code opener`() {
    val input = "Run `"
    val result = sanitizePartialMarkdown(input)
    assertEquals("Run ", result)
  }

  @Test
  fun `sanitizer strips trailing bare strikethrough opener`() {
    val input = "Done ~~"
    val result = sanitizePartialMarkdown(input)
    assertEquals("Done ", result)
  }

  @Test
  fun `sanitizer still closes a bold opener once it has content`() {
    val input = "The **space"
    val result = sanitizePartialMarkdown(input)
    assertEquals("The **space**", result)
  }

  @Test
  fun `sanitizer preserves a complete trailing bold span`() {
    val input = "This is **bold**"
    val result = sanitizePartialMarkdown(input)
    assertEquals(input, result)
  }

  @Test
  fun `sanitizer rebalances a partial bold closer`() {
    // Only the first asterisk of the closing ** has arrived.
    val input = "I was **capturing*"
    val result = sanitizePartialMarkdown(input)
    assertEquals("I was **capturing**", result)
  }

  @Test
  fun `sanitizer rebalances a partial strikethrough closer`() {
    val input = "this is ~~struck~"
    val result = sanitizePartialMarkdown(input)
    assertEquals("this is ~~struck~~", result)
  }

  // endregion

  // region sanitizePartialMarkdown — code-aware counting + escapes

  @Test
  fun `sanitizer ignores asterisks inside a complete inline code span`() {
    val input = "The `a * b` value is set."
    val result = sanitizePartialMarkdown(input)
    assertEquals(input, result)
  }

  @Test
  fun `sanitizer closes inline code containing asterisks without leaking a marker`() {
    val input = "The `a * b"
    val result = sanitizePartialMarkdown(input)
    assertEquals("The `a * b`", result)
  }

  @Test
  fun `sanitizer ignores asterisks inside a fenced code block`() {
    val input = "```\nx = a ** b\n```"
    val result = sanitizePartialMarkdown(input)
    assertEquals(input, result)
  }

  @Test
  fun `sanitizer does not strip an escaped trailing asterisk`() {
    val input = "show a literal \\*"
    val result = sanitizePartialMarkdown(input)
    assertEquals(input, result)
  }

  // endregion

  // region RETURN_PARTIAL failure policy tests

  @Test
  fun `partial parser does not throw on incomplete markdown`() {
    val partialParser = CommonmarkAstNodeParser(CommonMarkdownParseOptions.Streaming)
    val result = partialParser.parse("**unclosed bold and *nested italic")
    assertEquals(AstDocument, result.type)
    assertNotNull(result.links.firstChild)
  }

  @Test
  fun `partial parser handles empty input`() {
    val partialParser = CommonmarkAstNodeParser(CommonMarkdownParseOptions.Streaming)
    val result = partialParser.parse("")
    assertEquals(AstDocument, result.type)
  }

  @Test
  fun `partial parser handles unclosed fenced code block in streaming`() {
    val partialParser = CommonmarkAstNodeParser(CommonMarkdownParseOptions.Streaming)
    val result = partialParser.parse("# Title\n\n```kotlin\nval x = 1")
    assertEquals(AstDocument, result.type)
    // Should have both heading and code block children
    assertNotNull(result.links.firstChild)
  }

  // endregion

  // region streaming sweep — no garble in ANY prefix (streaming bug hunt)
  // Stream each realistic fixture one character at a time and assert no prefix
  // ever leaks a literal emphasis/code marker into rendered *text*. Code spans
  // legitimately contain markers, so only AstText literals are checked. This is
  // the adversarial net the opener/closer regressions slipped past.

  @Test
  fun `streaming sweep leaks no literal markers into rendered text for any prefix`() {
    val parser = CommonmarkAstNodeParser(CommonMarkdownParseOptions.Streaming)
    val failures = mutableListOf<String>()
    for (fixture in STREAMING_SWEEP_FIXTURES) {
      for (len in 0..fixture.length) {
        val prefix = fixture.substring(0, len)
        val leaked = collectTextLiterals(parser.parse(prefix))
          .filter { it.contains('*') || it.contains('`') || it.contains('~') }
        if (leaked.isNotEmpty()) failures += "prefix=<<$prefix>> leaked=$leaked"
      }
    }
    assertTrue(
      failures.isEmpty(),
      "Streaming sweep leaked literal markers into text:\n" + failures.joinToString("\n"),
    )
  }

  // endregion
}

/** Collects the literal of every AstText node (regular text only; code spans/blocks excluded). */
private fun collectTextLiterals(root: AstNode): List<String> {
  val out = mutableListOf<String>()
  val stack = ArrayDeque<AstNode>()
  stack.addLast(root)
  while (stack.isNotEmpty()) {
    val node = stack.removeLast()
    (node.type as? AstText)?.let { out.add(it.literal) }
    var child = node.links.firstChild
    while (child != null) {
      stack.addLast(child)
      child = child.links.next
    }
  }
  return out
}

/**
 * Realistic Pi-style responses for the streaming sweep. Plain text deliberately
 * contains NO literal *, `, or ~~ (only formatting uses them), so any such
 * character appearing in rendered text means the sanitizer leaked a partial
 * delimiter.
 */
private val STREAMING_SWEEP_FIXTURES = listOf(
  "I was **capturing** the moment. The **space** between every **word pair** matters.",
  "This is **bold** and *italic* and `code` and ~~struck~~, all together now.",
  "Mixed **bold with *nested italic* inside** and a trailing **bold** word.",
  "Use the `parser.parse(text)` call, then **render** the `node` it returns.",
  "Visit [the docs](https://example.com/streaming) and [the ticket](https://example.com/pi) now.",
  "Nested **outer `inner code` outer** then a final ~~strike~~ here.",
  "## Heading line\n\nA paragraph with **bold** and a list:\n\n- first **item**\n- second `item`\n- third *item*",
  "***Important***: read the **`config`** file and the *notes* below carefully.",
  "Steps are **one**, **two**, and **three**, then ~~skip~~ the last one.",
  "Click **[the dashboard](https://example.com/dash)** and then *confirm* it.",
  "Edge cases: ***triple***, then **bold**, then *italic*, then `code` mixed.",
  "The `arr[i] * 2` expression and `kwargs` are passed to **build** then done.",
  "Inline `a ** b` math, then **bold**, then `c ~~ d` and back to plain text.",
  "Power op:\n\n```py\nx = a ** b  # not bold\n```\n\nThen a **bold** word after.",
)

/** Simple recursive function that recurses [n] times to demonstrate stack overflow. */
private fun countRecursively(n: Int): Int {
  if (n <= 0) return 0
  return 1 + countRecursively(n - 1)
}
