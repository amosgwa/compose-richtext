package com.halilibo.richtext.commonmark

/**
 * Controls how the parser handles malformed or incomplete markdown input.
 */
public enum class FailurePolicy {
  /**
   * Throw an exception when the input cannot be parsed into a valid AST.
   * This is the default behavior.
   */
  STRICT,

  /**
   * Return a best-effort partial AST instead of throwing. Useful for rendering
   * markdown that is still being received (e.g. streaming / incremental input)
   * where unclosed delimiters and incomplete structures are expected.
   */
  RETURN_PARTIAL
}

/**
 * Allows configuration of the Markdown parser
 *
 * @param autolink Detect plain text links and turn them into Markdown links.
 * @param failurePolicy How to handle parse errors — throw or return a partial result.
 */
public class CommonMarkdownParseOptions(
  public val autolink: Boolean,
  public val failurePolicy: FailurePolicy = FailurePolicy.STRICT
) {

  override fun toString(): String {
    return "CommonMarkdownParseOptions(autolink=$autolink, failurePolicy=$failurePolicy)"
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is CommonMarkdownParseOptions) return false

    if (autolink != other.autolink) return false
    if (failurePolicy != other.failurePolicy) return false

    return true
  }

  override fun hashCode(): Int {
    var result = autolink.hashCode()
    result = 31 * result + failurePolicy.hashCode()
    return result
  }

  public fun copy(
    autolink: Boolean = this.autolink,
    failurePolicy: FailurePolicy = this.failurePolicy
  ): CommonMarkdownParseOptions = CommonMarkdownParseOptions(
    autolink = autolink,
    failurePolicy = failurePolicy
  )

  public companion object {
    public val Default: CommonMarkdownParseOptions = CommonMarkdownParseOptions(
      autolink = true
    )

    public val Streaming: CommonMarkdownParseOptions = CommonMarkdownParseOptions(
      autolink = true,
      failurePolicy = FailurePolicy.RETURN_PARTIAL
    )
  }
}
