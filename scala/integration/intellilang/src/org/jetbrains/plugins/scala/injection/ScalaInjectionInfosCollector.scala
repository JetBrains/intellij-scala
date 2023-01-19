package org.jetbrains.plugins.scala.injection

import com.intellij.lang.{ASTNode, Language}
import com.intellij.openapi.util.TextRange
import com.intellij.psi._
import org.intellij.plugins.intelliLang.inject.InjectorUtils.InjectionInfo
import org.intellij.plugins.intelliLang.inject._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.ScInterpolatedStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.util.MultilineStringUtil

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

//TODO: (maybe?) optimize to use buffers and less flatMaps, because ScalaLanguageInjector.getLanguagesToInject is quite a hot method
private object ScalaInjectionInfosCollector {

  /**
   * Placeholder text for injections in interpolated strings<br>
   * E.g. s"hello ${name}" will have text "hello InjectionPlaceholder".
   * Note that the text is mainly needed for tests. It's not supposed to be shown to the user anywhere.
   */
  private val InjectionPlaceholder = "InjectionPlaceholder"

  case class InjectionSplitResult(isUnparseable: Boolean, ranges: collection.Seq[InjectionInfo])

  /**
   * This method splits the original host (string literal or concatenation of string literals) into a sequence of injection infos.
   * Each InjectionInfo represents a range in host which has some content with injected language.
   * Note that we can't just return whole string literal range.
   * Some parts from the host do not represent actual string content.
   * For example:
   *  - in multiline string with margins leading spaces and margin char `|` shouldn't be represented by any info,
   *    because they are not added to the final string content (after margins are stripped with `stripMargin`)
   *  - in interpolated strings injections (`s"Hello $injectedValue"`) shouldn't be included as well (see e.g. #SCL-20707)
   *
   * @param hostStrings represents string literal or concatenation of string literals
   * @param language    language to inject
   * @param prefix      original injection prefix for the host
   * @param suffix      original injection suffix for the host
   * @note Initially some of the logic in thi collector was inspired by ideas in
   *       org.jetbrains.kotlin.idea.injection.InterpolatedStringInjectorProcessor
   *       (Kotlin also has interpolated strings and I used it to see examples of how injections in interpolated strings are handled)
   */
  def collectInjectionInfos(
    hostStrings: Seq[ScStringLiteral],
    language: Language,
    prefix: String,
    suffix: String
  ): InjectionSplitResult = {
    val result = new ArrayBuffer[InjectionInfo]()
    var isUnparseable = false
    hostStrings.iterator.zipWithIndex.foreach { case (literal, literalIdx) =>
      val isFirstLiteral = literalIdx == 0
      val isLastLiteral = literalIdx == hostStrings.size - 1

      val prefixForLiteral = if (isFirstLiteral) prefix else ""
      val suffixForLiteral = if (isLastLiteral) suffix else ""

      isUnparseable |= collectInjectionInfosForString(
        literal,
        language,
        prefixForLiteral,
        suffixForLiteral
      )(result)
    }
    InjectionSplitResult(isUnparseable, result)
  }

  private def collectInjectionInfosForString(
    string: ScStringLiteral,
    language: Language,
    prefix: String,
    suffix: String
  )(result: mutable.Buffer[InjectionInfo]): Boolean = {
    val languageId = language.getID
    string match {
      case interpolated: ScInterpolatedStringLiteral =>
        collectInjectionInfosForInterpolatedString(interpolated, languageId, prefix, suffix)(result)

      case _ if string.isMultiLineString =>
        collectInjectionInfosForMultilineString(string, languageId, prefix, suffix)(result)
        false

      case _ =>
        val range = getRangeInElement(string)
        val injectedLanguage = newInjectedLanguage(languageId, prefix, suffix)
        result += new InjectionInfo(string, injectedLanguage, range)
        false
    }
  }

  /** @return whether string literal has at least single injection */
  private def collectInjectionInfosForInterpolatedString(
    literal: ScInterpolatedStringLiteral,
    languageId: String,
    prefix: String,
    suffix: String,
  )(result: mutable.Buffer[InjectionInfo]): Boolean = {
    import ScalaTokenTypes._

    val children: Array[ASTNode] =
      literal.getNode.getChildren(null)

    lazy val literalText = literal.getText
    lazy val marginChar = MultilineStringUtil.getMarginChar(literal)

    var baseOffset = 0

    var hasInjection = false
    var childIdx = 0
    var prevChild: ASTNode = null
    while (childIdx < children.length) {
      val child = children(childIdx)

      val childTextLength = child.getTextLength

      //0-th child represents interpolator and 1-th represents first content child
      val isFirstPart = childIdx == 1
      //last children represents closing quotes, so last content children is before it
      val isLastPart = childIdx == children.length - 2

      val partPrefix = if (isFirstPart) prefix else InjectionPlaceholder
      val partSuffix = if (isLastPart) suffix else ""
      val injectedLanguageForPart = newInjectedLanguage(languageId, partPrefix, partSuffix)

      //NOTE: for the first content child parser also captures opening quote(s), `"` or `"""` (for multiline strings)
      //we need to exclude them from the range
      val firstPartShift = if (isFirstPart) child.getElementType match {
        case `tINTERPOLATED_STRING` => 1
        case `tINTERPOLATED_MULTILINE_STRING` => 3
        case _ => 0
      } else 0

      val start = baseOffset + firstPartShift
      val end = baseOffset + childTextLength

      //NOTE: for the first content child parser also captures opening quote(s), `"` or `"""` (for multiline strings)
      //we need to exclude them from the range
      child.getElementType match {
        case `tINTERPOLATED_MULTILINE_STRING` =>
          val contentText = literalText.substring(start, end)
          val ranges = extractMultiLineStringRanges(contentText, start, marginChar)
          collectInjectionInfosForMultilineString(literal, ranges, languageId, partPrefix, partSuffix)(result)
        case `tINTERPOLATED_STRING` =>
          result += new InjectionInfo(literal, injectedLanguageForPart, TextRange.create(start, end))
        case _ if prevChild != null && prevChild.getPsi.is[ScExpression] =>
          //we are just right after injection (in the end of the string or between two injections with no content between them)
          result += new InjectionInfo(literal, injectedLanguageForPart, TextRange.from(baseOffset, 0))
        case _ =>
      }

      hasInjection |= child.getElementType == tINTERPOLATED_STRING_INJECTION
      prevChild = child
      baseOffset += child.getTextLength
      childIdx += 1
    }

    hasInjection
  }

  private def collectInjectionInfosForMultilineString(
    literal: ScStringLiteral,
    languageId: String,
    prefix: String,
    suffix: String,
  )(result: mutable.Buffer[InjectionInfo]): Unit = {
    val ranges = extractMultiLineStringRanges(literal)
    collectInjectionInfosForMultilineString(literal, ranges, languageId, prefix, suffix)(result)
  }

  private def collectInjectionInfosForMultilineString(
    literal: ScStringLiteral,
    lineRanges: Seq[TextRange],
    languageId: String,
    prefix: String,
    suffix: String,
  )(result: mutable.Buffer[InjectionInfo]): Unit = {
    lineRanges.iterator.zipWithIndex.foreach { case (range, rangeIdx) =>
      val isFirstRange = rangeIdx == 0
      val isLastRange = rangeIdx == lineRanges.length - 1

      val rangePrefix = if (isFirstRange) prefix else ""
      val rangeSuffix = if (isLastRange) suffix else ""

      //capture new line symbol using `lineRange.grown(1)` for all lines except the last
      val rangeActual = if (isLastRange) range else range.grown(1)
      val injectedLanguage = newInjectedLanguage(languageId, rangePrefix, rangeSuffix)
      result += new InjectionInfo(literal, injectedLanguage, rangeActual)
    }
  }

  private def extractMultiLineStringRanges(multilineString: ScStringLiteral): Seq[TextRange] = {
    val contentRange = getRangeInElement(multilineString)
    val contentText = contentRange.substring(multilineString.getText)

    val marginChar = MultilineStringUtil.getMarginChar(multilineString)

    extractMultiLineStringRanges(
      contentText,
      contentRange.getStartOffset,
      marginChar
    )
  }

  private def extractMultiLineStringRanges(
    multilineContent: String,
    contentOffsetHost: Int,
    marginChar: Char
  ): Seq[TextRange] = {
    val rangesCollected = mutable.ListBuffer[TextRange]()

    var baseOffset = contentOffsetHost

    val lines = multilineContent.linesIterator
    for (line <- lines) {
      val lineLength = line.length
      val wsPrefixLength = line.segmentLength(_.isWhitespace)

      val lineHasMargin = wsPrefixLength < line.length && line.charAt(wsPrefixLength) == marginChar

      val shift = if (lineHasMargin) wsPrefixLength + 1 else 0
      val start = baseOffset + shift
      val end = lineLength - shift
      rangesCollected += TextRange.from(start, end)

      baseOffset += lineLength + 1
    }

    if (multilineContent.endsWith('\n')) {
      // last empty line is not treat as a line by `linesIterator`,
      // but we need to add an empty range in order to be able to edit this line in `Edit code fragment` panel
      val end = baseOffset + 1
      rangesCollected += TextRange.create(end, end)
    }

    if (rangesCollected.isEmpty) {
      rangesCollected += TextRange.create(contentOffsetHost, contentOffsetHost)
    }

    rangesCollected.toList
  }

  private def getRangeInElement(literal: ScStringLiteral): TextRange =
    ElementManipulators.getNotNullManipulator(literal).getRangeInElement(literal)

  private def newInjectedLanguage(languageId: String, prefix: String, suffix: String) = {
    //NOTE: seems that "isDynamic" argument doesn't affect anything
    //org.intellij.plugins.intelliLang.inject.InjectedLanguage.isDynamic seems to be unused in the platform
    InjectedLanguage.create(languageId, prefix, suffix, false)
  }
}
