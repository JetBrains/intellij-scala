package org.jetbrains.plugins.scala
package format

import com.intellij.psi.PsiElement
import lang.psi.api.base.ScInterpolatedStringLiteral
import lang.psi.api.expr.{ScBlockExpr, ScExpression}
import lang.lexer.ScalaTokenTypes

/**
 * Pavel Fatin
 */

object InterpolatedStringParser extends StringParser {
  private val FormatSpecifierPattern = "^%(\\d+\\$)?([-#+ 0,(\\<]*)?(\\d+)?(\\.\\d+)?([tT])?([a-zA-Z%])".r

  def parse(element: PsiElement) = Some(element) collect {
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
            case Some(e) if e.getNode.getElementType == ScalaTokenTypes.tINTERPOLATED_STRING =>
              FormatSpecifierPattern.findFirstIn(e.getText).map(format => Specifier(Span(e, 0, format.length), format))
            case _ => None
          }
          Injection(actualExpression, specifier)

        case (e, _) if e.getNode.getElementType == ScalaTokenTypes.tINTERPOLATED_STRING =>
          val text = {
            val s = e.getText
            if (!formatted) s else
              FormatSpecifierPattern.findFirstIn(s).map(format => s.substring(format.length)).getOrElse(s)
          }
          Text(text)
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
