package com.halilibo.richtext.commonmark

import com.halilibo.richtext.markdown.node.AstBlockQuote
import com.halilibo.richtext.markdown.node.AstCode
import com.halilibo.richtext.markdown.node.AstDocument
import com.halilibo.richtext.markdown.node.AstEmphasis
import com.halilibo.richtext.markdown.node.AstFencedCodeBlock
import com.halilibo.richtext.markdown.node.AstHardLineBreak
import com.halilibo.richtext.markdown.node.AstHeading
import com.halilibo.richtext.markdown.node.AstHtmlBlock
import com.halilibo.richtext.markdown.node.AstHtmlInline
import com.halilibo.richtext.markdown.node.AstImage
import com.halilibo.richtext.markdown.node.AstIndentedCodeBlock
import com.halilibo.richtext.markdown.node.AstLink
import com.halilibo.richtext.markdown.node.AstLinkReferenceDefinition
import com.halilibo.richtext.markdown.node.AstListItem
import com.halilibo.richtext.markdown.node.AstNode
import com.halilibo.richtext.markdown.node.AstNodeLinks
import com.halilibo.richtext.markdown.node.AstNodeType
import com.halilibo.richtext.markdown.node.AstOrderedList
import com.halilibo.richtext.markdown.node.AstParagraph
import com.halilibo.richtext.markdown.node.AstSoftLineBreak
import com.halilibo.richtext.markdown.node.AstStrikethrough
import com.halilibo.richtext.markdown.node.AstStrongEmphasis
import com.halilibo.richtext.markdown.node.AstTableBody
import com.halilibo.richtext.markdown.node.AstTableCell
import com.halilibo.richtext.markdown.node.AstTableCellAlignment
import com.halilibo.richtext.markdown.node.AstTableHeader
import com.halilibo.richtext.markdown.node.AstTableRoot
import com.halilibo.richtext.markdown.node.AstTableRow
import com.halilibo.richtext.markdown.node.AstText
import com.halilibo.richtext.markdown.node.AstThematicBreak
import com.halilibo.richtext.markdown.node.AstUnorderedList
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableBody
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableCell.Alignment.CENTER
import org.commonmark.ext.gfm.tables.TableCell.Alignment.LEFT
import org.commonmark.ext.gfm.tables.TableCell.Alignment.RIGHT
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.CustomBlock
import org.commonmark.node.CustomNode
import org.commonmark.node.Document
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.LinkReferenceDefinition
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser

/**
 * Holds the data for a pending conversion task in the iterative tree traversal.
 */
private class ConvertWorkItem(
  val startNode: Node,
  val parentAstNode: AstNode?,
  val initialPrev: AstNode?,
  val onFirstCreated: (AstNode?) -> Unit
)

/**
 * Maps a CommonMark [Node] to its corresponding [AstNodeType].
 * Returns null for unrecognized node types (CustomNode, CustomBlock, etc.).
 */
private fun convertNodeType(node: Node): AstNodeType? = when (node) {
  is BlockQuote -> AstBlockQuote
  is BulletList -> AstUnorderedList(bulletMarker = node.bulletMarker)
  is Code -> AstCode(literal = node.literal)
  is Document -> AstDocument
  is Emphasis -> AstEmphasis(delimiter = node.openingDelimiter)
  is FencedCodeBlock -> AstFencedCodeBlock(
    literal = node.literal,
    fenceChar = node.fenceChar,
    fenceIndent = node.fenceIndent,
    fenceLength = node.fenceLength,
    info = node.info
  )
  is HardLineBreak -> AstHardLineBreak
  is Heading -> AstHeading(
    level = node.level
  )
  is ThematicBreak -> AstThematicBreak
  is HtmlInline -> AstHtmlInline(
    literal = node.literal
  )
  is HtmlBlock -> AstHtmlBlock(
    literal = node.literal
  )
  is Image -> {
    if (node.destination == null) {
      null
    }
    else {
      AstImage(
        title = node.title ?: "",
        destination = node.destination
      )
    }
  }
  is IndentedCodeBlock -> AstIndentedCodeBlock(
    literal = node.literal
  )
  is Link -> AstLink(
    title = node.title ?: "",
    destination = node.destination
  )
  is ListItem -> AstListItem
  is OrderedList -> AstOrderedList(
    startNumber = node.startNumber,
    delimiter = node.delimiter
  )
  is Paragraph -> AstParagraph
  is SoftLineBreak -> AstSoftLineBreak
  is StrongEmphasis -> AstStrongEmphasis(
    delimiter = node.openingDelimiter
  )
  is Text -> AstText(
    literal = node.literal
  )
  is LinkReferenceDefinition -> AstLinkReferenceDefinition(
    title = node.title ?: "",
    destination = node.destination,
    label = node.label
  )
  is TableBlock -> AstTableRoot
  is TableHead -> AstTableHeader
  is TableBody -> AstTableBody
  is TableRow -> AstTableRow
  is TableCell -> AstTableCell(
    header = node.isHeader,
    alignment = when (node.alignment) {
      LEFT -> AstTableCellAlignment.LEFT
      CENTER -> AstTableCellAlignment.CENTER
      RIGHT -> AstTableCellAlignment.RIGHT
      null -> AstTableCellAlignment.LEFT
      else -> AstTableCellAlignment.LEFT
    }
  )
  is Strikethrough -> AstStrikethrough(
    node.openingDelimiter
  )
  is CustomNode -> null
  is CustomBlock -> null
  else -> null
}

/**
 * Converts common-markdown tree to AstNode tree iteratively using an explicit stack,
 * avoiding StackOverflowError on deeply nested or long markdown documents.
 */
internal fun convert(
  node: Node?,
  parentNode: AstNode? = null,
  previousNode: AstNode? = null,
): AstNode? {
  node ?: return null

  var result: AstNode? = null
  val stack = ArrayDeque<ConvertWorkItem>()
  stack.addLast(ConvertWorkItem(node, parentNode, previousNode) { result = it })

  while (stack.isNotEmpty()) {
    val item = stack.removeLast()

    var prev: AstNode? = null
    var firstCreated: AstNode? = null
    var cmNode: Node? = item.startNode
    var nullTypeNode: Node? = null

    // Iterate through siblings instead of recursing
    while (cmNode != null) {
      val nodeType = convertNodeType(cmNode)
      val newNode = nodeType?.let {
        AstNode(it, AstNodeLinks(
          parent = item.parentAstNode,
          previous = prev ?: item.initialPrev
        ))
      }

      if (newNode != null) {
        if (firstCreated == null) firstCreated = newNode
        prev?.links?.next = newNode

        // Push child processing onto the explicit stack instead of recursing
        val child = cmNode.firstChild
        if (child != null) {
          stack.addLast(ConvertWorkItem(child, newNode, null) { newNode.links.firstChild = it })
        }

        prev = newNode
        cmNode = cmNode.next
      } else {
        // Unrecognized node type — stop sibling chain (preserves original behavior)
        nullTypeNode = cmNode
        cmNode = null
      }
    }

    // Set lastChild on the parent, matching the original recursive behavior
    if (nullTypeNode != null) {
      if (nullTypeNode.next == null) {
        item.parentAstNode?.links?.lastChild = null
      }
    } else {
      item.parentAstNode?.links?.lastChild = prev
    }

    item.onFirstCreated(firstCreated)
  }

  return result
}

/**
 * Closes unclosed inline delimiters in markdown text so that a CommonMark parser
 * can produce a valid AST from incomplete / streaming input.
 *
 * Handles: fenced code blocks (``` or ~~~), inline code (`), bold (**),
 * italic (*), and strikethrough (~~).
 */
internal fun sanitizePartialMarkdown(text: String): String {
  val sb = StringBuilder(text)

  // 0. Strip trailing backslash — it may be the start of an escape sequence
  //    that hasn't arrived yet. Rendering a bare "\" looks wrong.
  if (sb.isNotEmpty() && sb[sb.length - 1] == '\\') {
    sb.deleteCharAt(sb.length - 1)
  }

  // 1. Close unclosed fenced code blocks (``` or ~~~)
  var fencePattern: String? = null
  for (line in text.lines()) {
    val trimmed = line.trimStart()
    if (fencePattern != null) {
      if (trimmed.startsWith(fencePattern)) {
        fencePattern = null
      }
    } else {
      if (trimmed.startsWith("```")) {
        fencePattern = "```"
      } else if (trimmed.startsWith("~~~")) {
        fencePattern = "~~~"
      }
    }
  }
  val hadOpenFence = fencePattern != null
  if (fencePattern != null) {
    sb.append("\n").append(fencePattern)
  }

  // 2. Strip incomplete link/image syntax from the end of the text.
  //    Incomplete links like "[text](http://partial" would produce a broken
  //    clickable link, so we remove the entire construct and render only the
  //    display text (if any) as plain text.
  stripIncompleteLinks(sb)

  // 2.5. Strip a trailing run of inline delimiters that hasn't finished
  //      streaming — a bare opener ("The **") or a partial closer ("**word*").
  //      Both otherwise get over-closed into empty spans ("The ****",
  //      "**word****") that render as literal **/**** garble. Dropping the
  //      unfinished run lets step 3 rebalance against the real openers. Skipped
  //      right after closing a fence, where a trailing run is the appended fence
  //      marker / literal code, not an inline delimiter.
  if (!hadOpenFence) {
    stripTrailingPartialDelimiters(sb)
  }

  // 3. Balance unclosed inline delimiters, ignoring anything inside code
  //    (inline spans and fenced blocks) where * / ` / ~ are literal.
  val content = sb.toString()
  val suffixes = computeClosingSuffixes(content)

  // Insert the closing delimiters immediately after the last non-whitespace
  // character. Appending them after trailing whitespace would leave a space
  // before the closer ("**word **"), which CommonMark refuses to bind (closers
  // can't be preceded by whitespace) — so the markers would render literally.
  var insertAt = sb.length
  while (insertAt > 0 && sb[insertAt - 1].isWhitespace()) insertAt--
  for (suffix in suffixes) {
    sb.insert(insertAt, suffix)
    insertAt += suffix.length
  }

  return sb.toString()
}

/**
 * Removes a trailing run of inline emphasis/code delimiters (`*`, `~`, `` ` ``)
 * that hasn't finished streaming. Two cases both render as literal-marker garble
 * if left for the close-counting step:
 *  - a bare opener with no content yet — `The **` would get a `**` appended,
 *    producing the empty span `The ****`.
 *  - a partial closer mid-arrival — `**word*` (only the first `*` of the closing
 *    `**` has arrived) over-appends to `**word****`, rendering bold "word" plus a
 *    literal `**`.
 * Dropping the unfinished run lets the close-counting step rebalance against the
 * real openers, so the text renders cleanly until the delimiter finishes
 * arriving (e.g. `**word*` -> `**word` -> `**word**`).
 *
 * A run preceded by a newline is a line-start construct (e.g. a closing code
 * fence) and is left untouched; an escaped delimiter (`\*`) is a literal
 * character and is never stripped.
 */
private fun stripTrailingPartialDelimiters(sb: StringBuilder) {
  // Locate the trailing delimiter run, ignoring any trailing whitespace.
  var end = sb.length
  while (end > 0 && sb[end - 1].isWhitespace()) end--
  var runStart = end
  while (runStart > 0) {
    val ch = sb[runStart - 1]
    if (ch != '*' && ch != '~' && ch != '`') break
    // Stop at an escaped delimiter (odd number of preceding backslashes): it is a
    // literal character, not markup, so it must not be stripped.
    var b = runStart - 2
    var backslashes = 0
    while (b >= 0 && sb[b] == '\\') { backslashes++; b-- }
    if (backslashes % 2 == 1) break
    runStart--
  }
  if (runStart == end) return // no trailing (unescaped) delimiter run
  val before = if (runStart > 0) sb[runStart - 1] else ' '
  if (before == '\n' || before == '\r') {
    // Line-start run. Keep a real fence marker (>= 3 identical backticks/tildes,
    // e.g. a closing fence of a complete block); strip anything shorter — a
    // partial fence marker still streaming in, or a stray line-start emphasis
    // opener — which would otherwise render as literal markers.
    val run = sb.substring(runStart, end)
    val isFence = run.length >= 3 && (run.all { it == '`' } || run.all { it == '~' })
    if (isFence) return
  }
  sb.delete(runStart, sb.length)
}

/**
 * Computes the closing delimiters needed to balance [content], ignoring any
 * characters inside code — inline spans and fenced blocks — where `*`, `` ` ``,
 * and `~` are literal, not markup. Backslash-escaped delimiters outside code are
 * skipped too. Suffixes are returned in append order (backtick, bold, italic,
 * strikethrough) so a `***` closes both italic and bold.
 *
 * Fenced blocks are detected and closed separately by the caller; here they are
 * only skipped so their contents don't pollute the inline counts.
 */
private fun computeClosingSuffixes(content: String): List<String> {
  var inFence = false
  var fenceMarker = ""
  var inInlineCode = false
  var bold = 0
  var italic = 0
  var strike = 0
  var atLineStart = true
  var i = 0
  while (i < content.length) {
    // Fenced code blocks are line-based: skip their content entirely.
    if (atLineStart && !inInlineCode) {
      // Only the current line — trimStart() over the whole rest would eat the
      // newline and conflate a blank line with the line that follows it.
      val lineEnd = content.indexOf('\n', i).let { if (it == -1) content.length else it }
      val trimmed = content.substring(i, lineEnd).trimStart()
      if (inFence) {
        if (trimmed.startsWith(fenceMarker)) {
          inFence = false
          fenceMarker = ""
        }
        i = nextLineStart(content, i)
        atLineStart = true
        continue
      } else if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
        inFence = true
        fenceMarker = if (trimmed.startsWith("```")) "```" else "~~~"
        i = nextLineStart(content, i)
        atLineStart = true
        continue
      }
    }
    if (inFence) {
      i = nextLineStart(content, i)
      atLineStart = true
      continue
    }

    val c = content[i]
    when {
      c == '\n' -> {
        i++
        atLineStart = true
      }
      c == '\\' && !inInlineCode -> {
        i += 2 // escaped character — literal
        atLineStart = false
      }
      c == '`' -> {
        while (i < content.length && content[i] == '`') i++ // consume the backtick run
        inInlineCode = !inInlineCode
        atLineStart = false
      }
      inInlineCode -> {
        i++ // literal inside an inline code span
        atLineStart = false
      }
      c == '*' -> {
        var runLen = 0
        while (i < content.length && content[i] == '*') {
          runLen++
          i++
        }
        bold += runLen / 2
        italic += runLen % 2
        atLineStart = false
      }
      c == '~' && i + 1 < content.length && content[i + 1] == '~' -> {
        strike++
        i += 2
        atLineStart = false
      }
      else -> {
        i++
        atLineStart = false
      }
    }
  }
  return buildList {
    if (inInlineCode) add("`")
    if (bold % 2 != 0) add("**")
    if (italic % 2 != 0) add("*")
    if (strike % 2 != 0) add("~~")
  }
}

/** Index just past the next newline at or after [from], or the end of [content]. */
private fun nextLineStart(content: String, from: Int): Int {
  val nl = content.indexOf('\n', from)
  return if (nl == -1) content.length else nl + 1
}

/**
 * Finds and removes incomplete link or image syntax at the end of the text.
 *
 * Handles these truncation points:
 *  - `[text`                       → keep "text" as plain text
 *  - `[text](`                     → keep "text" as plain text
 *  - `[text](url`                  → keep "text" as plain text
 *  - `![alt`                       → keep "alt" as plain text
 *  - `![alt](url`                  → keep "alt" as plain text
 *
 * A fully closed `[text](url)` or `![alt](url)` is left untouched.
 */
private fun stripIncompleteLinks(sb: StringBuilder) {
  val text = sb.toString()

  // Case 0: `[label]` with nothing after the `]` yet — an incipient link or
  // footnote whose `(url)` (or nothing) hasn't streamed in. It's undecidable at
  // this instant whether a link is coming, so render the bare label and let the
  // brackets/link "appear" when it resolves (plain -> [1] / plain -> link). That
  // additive transition reads better than showing `[label]` and then stripping
  // the brackets away. Only applies while `]` is the final char; once anything
  // follows it the construct has settled (a space rules out a link; a `(` is
  // handled by Case 1 below).
  if (text.endsWith("]")) {
    var depth = 0
    var j = text.length - 1
    while (j >= 0) {
      when (text[j]) {
        ']' -> depth++
        '[' -> {
          depth--
          if (depth == 0) {
            val isImage = j > 0 && text[j - 1] == '!'
            val removeFrom = if (isImage) j - 1 else j
            val label = text.substring(j + 1, text.length - 1)
            sb.delete(removeFrom, sb.length)
            sb.append(label)
            return
          }
        }
      }
      j--
    }
  }

  // Case 1: `[text](url` — bracket is closed but paren is not.
  // Find the last `](` and check if it has a matching `)`.
  val lastBracketParen = text.lastIndexOf("](")
  if (lastBracketParen != -1) {
    val afterParen = lastBracketParen + 2
    val closeParen = text.indexOf(')', afterParen)
    if (closeParen == -1) {
      // Incomplete url — find the matching `[` for this `]`
      val openBracket = findMatchingOpenBracket(text, lastBracketParen)
      if (openBracket != -1) {
        val isImage = openBracket > 0 && text[openBracket - 1] == '!'
        val removeFrom = if (isImage) openBracket - 1 else openBracket
        val displayText = text.substring(openBracket + 1, lastBracketParen)
        sb.delete(removeFrom, sb.length)
        sb.append(displayText)
        return
      }
    }
  }

  // Case 2: `[text` — bracket never closed. Find the last unmatched `[`.
  var bracketDepth = 0
  var i = text.length - 1
  while (i >= 0) {
    when (text[i]) {
      ']' -> bracketDepth++
      '[' -> {
        if (bracketDepth > 0) {
          bracketDepth--
        } else {
          // Found an unmatched open bracket
          val isImage = i > 0 && text[i - 1] == '!'
          val removeFrom = if (isImage) i - 1 else i
          val displayText = text.substring(i + 1)
          sb.delete(removeFrom, sb.length)
          sb.append(displayText)
          return
        }
      }
    }
    i--
  }
}

/**
 * Finds the `[` that matches the `]` at [closeBracketIndex] by scanning backwards.
 * Returns -1 if no matching open bracket is found.
 */
private fun findMatchingOpenBracket(text: String, closeBracketIndex: Int): Int {
  var depth = 0
  var i = closeBracketIndex - 1
  while (i >= 0) {
    when (text[i]) {
      ']' -> depth++
      '[' -> {
        if (depth > 0) depth-- else return i
      }
    }
    i--
  }
  return -1
}

public actual class CommonmarkAstNodeParser actual constructor(
  private val options: CommonMarkdownParseOptions
) {

  private val parser = Parser.builder()
    .extensions(
      listOfNotNull(
        TablesExtension.create(),
        StrikethroughExtension.create(),
        if (options.autolink) AutolinkExtension.create() else null
      )
    )
    .build()

  public actual fun parse(text: String): AstNode {
    val isPartial = options.failurePolicy == FailurePolicy.RETURN_PARTIAL
    val input = if (isPartial) sanitizePartialMarkdown(text) else text

    val commonmarkNode = parser.parse(input)
    if (commonmarkNode == null) {
      if (isPartial) {
        return AstNode(AstDocument, AstNodeLinks())
      }
      throw IllegalArgumentException(
        "Could not parse the given text content into a meaningful Markdown representation!"
      )
    }

    val result = convert(commonmarkNode)
    if (result == null) {
      if (isPartial) {
        return AstNode(AstDocument, AstNodeLinks())
      }
      throw IllegalArgumentException(
        "Could not convert the generated Commonmark Node into an ASTNode!"
      )
    }

    return result
  }
}

