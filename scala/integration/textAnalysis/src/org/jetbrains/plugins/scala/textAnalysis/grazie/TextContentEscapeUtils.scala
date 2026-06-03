package org.jetbrains.plugins.scala.textAnalysis.grazie

import com.intellij.grazie.text.TextContent
import com.intellij.grazie.text.TextContent.Exclusion
import com.intellij.grazie.utils.EscapeUtilsKt
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.extensions.IterableOnceExt
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.impl.base.literals.escapers.ScalaStringParser

import java.lang.{StringBuilder => JStringBuilder}
import scala.jdk.CollectionConverters.SeqHasAsJava

/**
 * Escape processing helpers for [[TextContent]].
 */
object TextContentEscapeUtils {

  /**
   * The method replaces backslash escape sequences in string literals according to the Scala rules.<br>
   * Some escape sequences are replaced with the content (for now only white space) and others are marked as unknown.
   *
   * @example `aaa \n bbb \u0024 ccc \\ ddd` -> `aaa \n bbb ? ccc ? ddd`
   * @note The logic in this method is similar to [[com.intellij.grazie.utils.EscapeUtilsKt#replaceBackslashEscapes]]
   *       but respects all the sophisiticated rules of Scala language
   * @note These rules are quite complicated and depend on following:
   * 1. type of string literal (plain/interpolated(s/f/raw), multilie/singleline)
   * 2. type of escape sequence (backslash/unicode/octal)
   * 3. scala verison and compiler options
   *
   * All of that is incapsulated in [[ScalaStringParser$]]
   */
  def replaceBackslashEscapes(content: TextContent, stringLiteral: ScStringLiteral): TextContent = {
    val escapeSequencesRanges: Seq[TextRange] = extractBackslashEscapedRanges(content, stringLiteral)
    val actions = buildBackslashEscapeActions(content.toString, escapeSequencesRanges)
    val result = applyBackslashEscapeActions(content, actions)
    result
  }


  private def extractBackslashEscapedRanges(content: TextContent, stringLiteral: ScStringLiteral): Seq[TextRange] = {
    // We gracefully handle incorrect escape sequences mostly for custom string interpolators (SCL-25082)
    // Performance note.
    // The current parsing implementation is not quite optimal in terms of RAM usage:
    //   1. We call `content.toString` to parse the TextContent because the `parse` method requires String and not CharSequence (which TextContent extends).
    //      In theory, it would be nice if we didn't have to do it and could work with CharSequences directly.
    // This is worth investigating in principle.
    // However, it seems that for now the most practical (without rewriting too much code) and correct solution is to use it as is.
    val unescapedText = new JStringBuilder()
    val outSourceOffsets = ScalaStringParser.parseFull(content.toString, stringLiteral, unescapedText)
    extractBackslashEscapedRanges(outSourceOffsets, unescapedText.length)
  }

  /**
   * Builds exclusions for escaped fragments using decoded-char -> source-char offsets.<br>
   * These offsets are produced by [[org.jetbrains.plugins.scala.lang.psi.impl.base.literals.escapers.ScalaStringParser]].
   *
   * Example input:
   * - original content: `example\ntext\twith\u0024escapes`
   * - `outSourceOffsets` (array tail is zero-filled and ignored):<br>
   *   `[0, 1, 2, 3, 4, 5, 6, 7, 9, 10, 11, 12, 13, 15, 16, 17, 18, 19, 25, 26, 27, 28, 29, 30, 31, 32, ...]`
   *
   * Resulting exclusions:
   * - `[7, 8]` for `\n`
   * - `[13, 14]` for `\t`
   * - `[19, 24]` for `\u0024`
   */
  private def extractBackslashEscapedRanges(outSourceOffsets: Array[Int], parsedLength: Int): Seq[TextRange] = {
    val ranges = Seq.newBuilder[TextRange]
    var i = 1
    while (i <= parsedLength) {
      val prevOffset = outSourceOffsets(i - 1)
      val currentOffset = outSourceOffsets(i)
      if (currentOffset - prevOffset > 1) {
        ranges += new TextRange(prevOffset, currentOffset)
      }
      i += 1
    }
    ranges.result()
  }

  private sealed trait BackslashEscapeAction
  private object BackslashEscapeAction {
    /**
     * The class indicates that a whitespace escape sequence should be replaced with a corresponding whitespace character (\n, \r, \t).
     *
     * @note The class only exists for "Whitespace" escape sequences and not for arbitrary escape sequences
     *       because current TextContent architecture/implementation can only handle whitespaces
     *       (via [[com.intellij.grazie.text.TextContentImpl.WSTokenInfo]]).
     *
     *       So, for example, we can't handle `\u0024` escape sequence and replace it with `$`. Same for \\ and \'.
     *       We have to mark it as unknown.
     *
     *       This is confirmed by Peter Gromov (as per 12 Mar 2026).
     * @param wsEscapeKind the code of the whitespace escape symbol without the `\`.<br>
     *                     Examples: `n` or `t` or `r`
     */
    final case class ReplaceWithWhitespace(range: TextRange, wsEscapeKind: Char) extends BackslashEscapeAction
    final case class MarkUnknown(range: TextRange) extends BackslashEscapeAction
  }

  import BackslashEscapeAction.{MarkUnknown, ReplaceWithWhitespace}

  /**
   * Builds actions for already extracted escaped ranges.
   *
   * Example:
   * source `a\\n b\\u0024 c\\f` -> Replace(`\n`), Replace(`$`), Unknown(`\f`)
   */
  private def buildBackslashEscapeActions(
    sourceText: String,
    escapedRanges: Seq[TextRange],
  ): Seq[BackslashEscapeAction] = {
    escapedRanges.map { range =>
      val escapeKind = sourceText.charAt(range.getStartOffset + 1)
      classifyEscapeAction(range, escapeKind)
    }
  }

  /**
   * Decides what to do with an extracted escape sequence using the char after `\`.
   *
   * Example:
   *  - `\n` -> Replace with a newline character
   *  - `\t` -> Replace with a tab character
   *  - `\u0024` -> Mark as unknown
   *  - `\f` -> Mark as unknown
   */
  private def classifyEscapeAction(range: TextRange, escapeKind: Char): BackslashEscapeAction = {
    escapeKind match {
      case 'n' | 'r' | 't' =>
        ReplaceWithWhitespace(range, escapeKind)

      // NOTE:
      // We intentionally treat the following as "unknown": Unicode escape sequences, `\'` and `\\`.
      // (see scaladoc of ReplaceWithWhitespace)
      //
      // Anyway, it should be fine in practice to just skip this.
      // For now, we don't have any real practical examples where this would be an issue.
      // Once we have, we may ask IntelliJ platform / Grazie developers to extend the model.
      case 'u' | '\'' | '\\' =>
        MarkUnknown(range)

      case _ =>
        MarkUnknown(range)
    }
  }

  /**
   * @note this is a more advanced alternative to [[EscapeUtilsKt#replaceBackslashEscapedWhitespace]].<br>
   *       It combines replacing whitespace escape sequences with whitespace characters and marking other escape sequences as unknown.
   */
  private def applyBackslashEscapeActions(content: TextContent, actions: Seq[BackslashEscapeAction]): TextContent = {
    val markAsUnknowns = actions.filterByType[MarkUnknown]
    val unknownExclusions = markAsUnknowns.map(_.range).map(Exclusion.markUnknown).asJava
    val contentWithExclusions = content.excludeRanges(unknownExclusions)

    val replaceWithWhitespace = actions.filterByType[ReplaceWithWhitespace]
    val wsChars = replaceWithWhitespace.map(_.wsEscapeKind).distinct

    contentWithExclusions.replaceBackslashEscapedWhitespace(wsChars)
  }

  private implicit class TextContentOps(private val content: TextContent) extends AnyVal {
    def replaceBackslashEscapedWhitespace(wsChars: Seq[Char]): TextContent = {
      // NOTE: ideally, EscapeUtilsKt should provide API to replace all whitespaces in a single pass.
      // In the current version of Grazie code (261.22158.46 March 5, 2026) this is not supported.
      // Peter Gromov mentioned that he might add dit later.
      // If you are reading this, please check if EscapeUtilsKt supports anything like this nowadays
      wsChars.foldLeft(content) { (acc, wsChar) =>
        EscapeUtilsKt.replaceBackslashEscapedWhitespace(acc, wsChar)
      }
    }
  }
}
