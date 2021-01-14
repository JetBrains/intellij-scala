package org.jetbrains.plugins.scala
package format

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.ScInterpolatedStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlockExpr, ScExpression}

import scala.collection.mutable
import scala.util.matching.Regex

/**
 * Pavel Fatin
 */
object InterpolatedStringParser extends StringParser {

  import FormattedStringParser.FormatSpecifierStartPattern

  override def parse(element: PsiElement): Option[Seq[StringPart]] =
    parse(element, checkStripMargin = true)

  private[format] def parse(element: PsiElement, checkStripMargin: Boolean): Option[Seq[StringPart]] = {
    if (checkStripMargin) element match {
      case WithStrippedMargin(_, _) => return StripMarginParser.parse(element)
      case _ =>
    }
    Some(element) collect {
      case literal: ScInterpolatedStringLiteral =>
        parseLiteral(literal)
    }
  }

  private def parseLiteral(literal: ScInterpolatedStringLiteral): Seq[StringPart] = {
    val formatted = literal.firstChild.exists(_.textMatches("f"))

    val pairs: Seq[(PsiElement, Option[PsiElement])] = {
      val elements = literal.children.toList.drop(1)
      elements.zipAll(elements.drop(1).map(Some(_)), null, None)
    }

    val parts = pairs.collect {
      // `str`, `{2 + 2}`
      // in input: s"$str ${2 + 2}"
      case (expression: ScExpression, nextOpt: Option[PsiElement]) =>
        val actualExpression = expression match {
          case block: ScBlockExpr =>
            val blockExpressions = block.exprs
            if (blockExpressions.length == 1) blockExpressions.head
            else block
          case it => it
        }
        val specifier = if (!formatted) None else nextOpt match {
          case Some(next) if isTextElement(next) =>
            val nextText = textIn(next)
            val matched = FormatSpecifierStartPattern.findFirstIn(nextText)
            matched.map { format =>
              Specifier(Span(next, 0, format.length), format)
            }
          case _ => None
        }
        Injection(actualExpression, specifier)

      // `text`
      // in input: s"${a}text${b}"
      case (e, _) if isTextElement(e) =>
        val text: String = {
          val value = textIn(e)
          // specifier was already handled in previous step, when handling injection, so drop it here
          if (formatted) FormatSpecifierStartPattern.replaceFirstIn(value, "")
          else value
        }
        Text(text)

      // `$$`
      // in input: s"$$"
      case (e, _) if e.getNode.getElementType == ScalaTokenTypes.tINTERPOLATED_STRING_ESCAPE =>
        Text("$")
    }

    val withFixedLeadingQuote = parts match {
      case Text(s) :: tail =>
        Text(s.drop(literal.quoteLength)) :: tail
      case it => it
    }
    val withEscapedPercents = if (!formatted) withFixedLeadingQuote else {
      withFixedLeadingQuote.flatMap {
        case t: Text => processSpecialFormatEscapes(t)
        case part    => List(part)
      }
    }
    val withoutEmpty = withEscapedPercents.filter {
      case Text("") => false
      case _        => true
    }
    withoutEmpty
  }

  private def isTextElement(e: PsiElement): Boolean = {
    val elementType = e.getNode.getElementType
    elementType == ScalaTokenTypes.tINTERPOLATED_STRING ||
      elementType == ScalaTokenTypes.tINTERPOLATED_MULTILINE_STRING
  }

  private def textIn(e: PsiElement): String = {
    val elementType = e.getNode.getElementType
    val text = e.getText
    elementType match  {
        // TODO: it's wrong to ignore multiline interpolated string SCL-18617
      case ScalaTokenTypes.tINTERPOLATED_STRING => StringUtil.unescapeStringCharacters(text)
      case ScalaTokenTypes.tINTERPOLATED_MULTILINE_STRING => text
    }
  }

  private val SpecialEscapeRegex = "(%%|%n)".r

  private def processSpecialFormatEscapes(textPart: Text): Seq[StringPart] = {
    val text = textPart.value

    val matches: Seq[Regex.Match] = SpecialEscapeRegex.findAllMatchIn(text).toList
    if (matches.isEmpty) List(textPart)
    else processSpecialFormatEscapes(text, matches)
  }

  private def processSpecialFormatEscapes(text: String, matches: Seq[Regex.Match]): Seq[StringPart] = {
    import SpecialFormatEscape._

    val result = new mutable.ArrayBuffer[StringPart](2 * matches.size + 1)

    var prevEnd = 0
    matches.foreach { m =>
      if (m.start > prevEnd) {
        result += Text(text.substring(prevEnd, m.start))
      }
      val specialEscape = m.matched match {
        case PercentChar.originalText   => PercentChar
        case LineSeparator.originalText => LineSeparator
      }
      result += specialEscape

      prevEnd = m.end
    }

    if (prevEnd < text.length)
      result += Text(text.substring(prevEnd, text.length))

    result.toSeq
  }

}
