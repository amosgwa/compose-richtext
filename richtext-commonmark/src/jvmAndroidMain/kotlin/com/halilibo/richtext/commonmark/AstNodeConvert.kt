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
  if (fencePattern != null) {
    sb.append("\n").append(fencePattern)
  }

  // 2. Strip incomplete link/image syntax from the end of the text.
  //    Incomplete links like "[text](http://partial" would produce a broken
  //    clickable link, so we remove the entire construct and render only the
  //    display text (if any) as plain text.
  stripIncompleteLinks(sb)

  // 3. Close unclosed inline delimiters by counting unescaped occurrences.
  //    Process the (potentially patched) text after prior fixes.
  val content = sb.toString()
  val suffixes = mutableListOf<String>()

  // Count unescaped backticks for inline code
  val backtickCount = countUnescaped(content, '`')
  if (backtickCount % 2 != 0) {
    suffixes.add("`")
  }

  // Count unescaped ** pairs for bold
  val boldCount = countPattern(content, "**")
  if (boldCount % 2 != 0) {
    suffixes.add("**")
  }

  // Count unescaped ~~ pairs for strikethrough
  val strikethroughCount = countPattern(content, "~~")
  if (strikethroughCount % 2 != 0) {
    suffixes.add("~~")
  }

  // Count unescaped single * for italic (exclude ** pairs already counted)
  val singleStarCount = countSingleDelimiters(content, '*')
  if (singleStarCount % 2 != 0) {
    suffixes.add("*")
  }

  for (suffix in suffixes) {
    sb.append(suffix)
  }

  return sb.toString()
}

private fun countUnescaped(text: String, char: Char): Int {
  var count = 0
  var i = 0
  while (i < text.length) {
    if (text[i] == '\\') {
      i += 2
      continue
    }
    if (text[i] == char) count++
    i++
  }
  return count
}

private fun countPattern(text: String, pattern: String): Int {
  var count = 0
  var i = 0
  while (i <= text.length - pattern.length) {
    if (text[i] == '\\') {
      i += 2
      continue
    }
    if (text.substring(i, i + pattern.length) == pattern) {
      // Make sure it's not a longer run (e.g. *** should not double-count **)
      val before = if (i > 0) text[i - 1] else ' '
      val after = if (i + pattern.length < text.length) text[i + pattern.length] else ' '
      if (before != pattern[0] && after != pattern[0]) {
        count++
      }
      i += pattern.length
    } else {
      i++
    }
  }
  return count
}

private fun countSingleDelimiters(text: String, char: Char): Int {
  var count = 0
  var i = 0
  while (i < text.length) {
    if (text[i] == '\\') {
      i += 2
      continue
    }
    if (text[i] == char) {
      // Check it's a single occurrence, not part of a double
      val next = if (i + 1 < text.length) text[i + 1] else ' '
      val prev = if (i > 0) text[i - 1] else ' '
      if (next != char && prev != char) {
        count++
      }
      i++
    } else {
      i++
    }
  }
  return count
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

