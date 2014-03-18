package org.jetbrains.plugins.scala
package format

import com.intellij.psi.PsiElement
import lang.psi.api.base.ScInterpolatedStringLiteral
import lang.psi.api.expr.{ScBlockExpr, ScExpression}
import lang.lexer.ScalaTokenTypes
import com.intellij.openapi.util.text.StringUtil

/**
 * Pavel Fatin
 */

object InterpolatedStringParser extends StringParser {
  private val FormatSpecifierPattern = "^%(\\d+\\$)?([-#+ 0,(\\<]*)?(\\d+)?(\\.\\d+)?([tT])?([a-zA-Z%])".r

  def parse(element: PsiElement) = parse(element, checkStripMargin = true)

  def parse(element: PsiElement, checkStripMargin: Boolean): Option[Seq[StringPart]] = {
    if (checkStripMargin) element match {
      case WithStrippedMargin(_, _) => return StripMarginParser.parse(element)
      case _ =>
    }
    Some(element) collect {
      case literal: ScInterpolatedStringLiteral =>
        val formatted = literal.firstChild.exists(_.getText == "f")

        val pairs = {
          val elements = literal.children.toList.drop(1)
          elements.zipAll(elements.drop(1).map(Some(_)), null, None)
        }

        val parts = pairs.collect {
          case (expression: ScExpression, next) =>
            val actualExpression = expression match {
              case block: ScBlockExpr => if (block.exprs.length > 1) block else block.exprs.headOption.getOrElse(block)
              case it => it
            }
            val specifier = if (!formatted) None else next match {
              case Some(e) if isTextElement(e) =>
                FormatSpecifierPattern.findFirstIn(textIn(e)).map(format => Specifier(Span(e, 0, format.length), format))
              case _ => None
            }
            Injection(actualExpression, specifier)

          case (e, _) if isTextElement(e) =>
            val text = {
              val s = textIn(e)
              if (!formatted) s else
                FormatSpecifierPattern.findFirstIn(s).map(format => s.substring(format.length)).getOrElse(s)
            }
            Text(text)
          case (e, _) if e.getNode.getElementType == ScalaTokenTypes.tINTERPOLATED_STRING_ESCAPE =>
            Text(e.getText.drop(1))
        }

        val cleanParts = parts match {
          case (Text(s) :: t) =>
            val edgeLength = if (literal.isMultiLineString) 3 else 1
            Text(s.drop(edgeLength)) :: t
          case it => it
        }

        cleanParts filter {
          case Text("") => false
          case _ => true
        }
    }
  }

  private def isTextElement(e: PsiElement) = {
    val elementType = e.getNode.getElementType
    elementType == ScalaTokenTypes.tINTERPOLATED_STRING ||
            elementType == ScalaTokenTypes.tINTERPOLATED_MULTILINE_STRING
  }

  private def textIn(e: PsiElement) = {
    val elementType = e.getNode.getElementType
    val text = e.getText
    elementType match  {
      case ScalaTokenTypes.tINTERPOLATED_STRING => StringUtil.unescapeStringCharacters(text)
      case ScalaTokenTypes.tINTERPOLATED_MULTILINE_STRING => text
    }
  }
}
