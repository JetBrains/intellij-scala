package org.jetbrains.plugins.scala
package format

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolatedStringLiteral, ScLiteral}
import org.jetbrains.plugins.scala.codeInspection.collections.MethodRepr
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes

/**
 * Nikolay.Tropin
 * 3/6/14
 */
object StripMarginParser extends StringParser {

  override def parse(element: PsiElement): Option[Seq[StringPart]] = Some(element) collect {
    case WithStrippedMargin(lit: ScInterpolatedStringLiteral, marginChar) =>
      val parts = InterpolatedStringParser.parse(lit).getOrElse(return None)
      parts.map {
        case Text(s) => Text(s.stripMargin(marginChar))
        case part => part
      }
    case WithStrippedMargin(lit: ScLiteral, marginChar) =>
      List(Text(lit.getValue.toString.stripMargin(marginChar)))
  }

}

object WithStrippedMargin {
  val STRIP_MARGIN = "stripMargin"

  def unapply(element: PsiElement): Option[(ScLiteral, Char)] = {
    element match {
      case MethodRepr(_, Some(lit: ScLiteral), Some(ref), Nil)
        if lit.isMultiLineString && ref.refName == STRIP_MARGIN => Some(lit, '|')
      case MethodRepr(_, Some(lit: ScLiteral), Some(ref), List(argLit: ScLiteral))
        if lit.isMultiLineString && ref.refName == STRIP_MARGIN &&
                argLit.getFirstChild.getNode.getElementType == ScalaTokenTypes.tCHAR => Some(lit, argLit.getValue.asInstanceOf[Char])
      case _ => None
    }
  }

}
