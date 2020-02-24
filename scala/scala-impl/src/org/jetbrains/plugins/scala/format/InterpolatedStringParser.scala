package org.jetbrains.plugins.scala
package format

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.ScInterpolatedStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlockExpr, ScExpression}

/**
 * Pavel Fatin
 */
object InterpolatedStringParser extends StringParser {
  private val FormatSpecifierPattern = "^%(\\d+\\$)?([-#+ 0,(\\<]*)?(\\d+)?(\\.\\d+)?([tT])?([a-zA-Z%])".r

  override def parse(element: PsiElement): Option[Seq[StringPart]] = parse(element, checkStripMargin = true)

  private[format] def parse(element: PsiElement, checkStripMargin: Boolean): Option[Seq[StringPart]] = {
    if (checkStripMargin) element match {
      case WithStrippedMargin(_, _) => return StripMarginParser.parse(element)
      case _ =>
    }
    Some(element) collect {
      case literal: ScInterpolatedStringLiteral =>
        parseLiteral(literal, element)
    }
  }

  private def parseLiteral(literal: ScInterpolatedStringLiteral, element: PsiElement): Seq[StringPart] = {
    val formatted = literal.firstChild.exists(_.textMatches("f"))

    val pairs: Seq[(PsiElement, Option[PsiElement])] = {
      val elements = literal.children.toList.drop(1)
      elements.zipAll(elements.drop(1).map(Some(_)), null, None)
    }

    val parts = pairs.collect {
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
            FormatSpecifierPattern.findFirstIn(nextText).map { format =>
              Specifier(Span(next, 0, format.length), format)
            }
          case _ => None
        }
        Injection(actualExpression, specifier)

      case (e, _) if isTextElement(e) =>
        val text: String = {
          val s = textIn(e)
          if (!formatted) s
          else FormatSpecifierPattern.findFirstIn(s).map(format => s.substring(format.length)).getOrElse(s)
        }
        Text(text)

      case (e, _) if e.getNode.getElementType == ScalaTokenTypes.tINTERPOLATED_STRING_ESCAPE =>
        Text(e.getText.drop(1))
    }

    val withFixedLeadingQuote = parts match {
      case Text(s) :: tail =>
        Text(s.drop(literal.quoteLength)) :: tail
      case it => it
    }
    val withEscapedPercents = withFixedLeadingQuote flatMap {
      case t: Text => t.withEscapedPercent(element.getManager)
      case part => List(part)
    }
    withEscapedPercents filter {
      case Text("") => false
      case _ => true
    }
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
      case ScalaTokenTypes.tINTERPOLATED_STRING => StringUtil.unescapeStringCharacters(text)
      case ScalaTokenTypes.tINTERPOLATED_MULTILINE_STRING => text
    }
  }
}
