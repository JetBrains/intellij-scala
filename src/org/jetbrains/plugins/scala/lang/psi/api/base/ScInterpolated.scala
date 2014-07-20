package org.jetbrains.plugins.scala
package lang.psi.api.base

import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.{PsiElement, PsiReference}
import org.jetbrains.plugins.scala.caches.CachesUtil
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScInterpolationPattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlockExpr, ScExpression, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.impl.expr.{ScInterpolatedPrefixReference, ScInterpolatedStringPartReference}

import scala.collection.mutable.ListBuffer

/**
 * @author kfeodorov
 * @since 03.03.14.
 */
trait ScInterpolated extends ScalaPsiElement {
  def isMultiLineString: Boolean

  def getReferencesToStringParts: Array[PsiReference] = {
    val accepted = List(ScalaTokenTypes.tINTERPOLATED_STRING, ScalaTokenTypes.tINTERPOLATED_MULTILINE_STRING)
    val res = ListBuffer[PsiReference]()
    val children: Array[PsiElement] = this match {
      case ip: ScInterpolationPattern => ip.args.children.toArray
      case sl: ScInterpolatedStringLiteral => Option(sl.getFirstChild.getNextSibling).toArray
    }
    for (child <- children) {
      if (accepted.contains(child.getNode.getElementType))
        res += new ScInterpolatedStringPartReference(child.getNode)
    }
    res.toArray
  }

  def getStringContextExpression: Option[ScExpression] = {
    def getExpandedExprBuilder(l: ScInterpolated) = {
      val quote = if (l.isMultiLineString) "\"\"\"" else "\""
      val parts = getStringParts(l).mkString(quote, s"$quote, $quote", quote) //making list of string literals
      val params = l.getInjections.map(_.getText).mkString("(", ",", ")")
      Option(ScalaPsiElementFactory.createExpressionWithContextFromText(
        s"_root_.scala.StringContext($parts).${getFirstChild.getText}$params", getContext, this))
    }

    CachesUtil.get(this, CachesUtil.STRING_CONTEXT_EXPANDED_EXPR_KEY,
      new CachesUtil.MyProvider[ScInterpolated, Option[ScExpression]](this, getExpandedExprBuilder)(PsiModificationTracker.MODIFICATION_COUNT))
  }

  def getInjections: Array[ScExpression] = {
    getNode.getChildren(null).flatMap {
      _.getPsi match {
        case a: ScBlockExpr => Array[ScExpression](a)
        case _: ScInterpolatedStringPartReference => Array[ScExpression]()
        case _: ScInterpolatedPrefixReference => Array[ScExpression]()
        case b: ScReferenceExpression => Array[ScExpression](b)
        case _ => Array[ScExpression]()
      }
    }
  }

  def getStringParts(l: ScInterpolated): Seq[String] = {
    val childNodes = l.children.map(_.getNode)
    val result = ListBuffer[String]()
    val emptyString = ""
    for {
      child <- childNodes
    } {
      child.getElementType match {
        case ScalaTokenTypes.tINTERPOLATED_STRING =>
          child.getText.headOption match {
            case Some('"') => result += child.getText.substring(1)
            case Some(_) => result += child.getText
            case None => result += emptyString
          }
        case ScalaTokenTypes.tINTERPOLATED_MULTILINE_STRING =>
          child.getText match {
            case s if s.startsWith("\"\"\"") => result += s.substring(3)
            case s: String => result += s
            case _ => result += emptyString
          }
        case ScalaTokenTypes.tINTERPOLATED_STRING_INJECTION | ScalaTokenTypes.tINTERPOLATED_STRING_END =>
          val prev = child.getTreePrev
          if (prev != null) prev.getElementType match {
            case ScalaTokenTypes.tINTERPOLATED_MULTILINE_STRING | ScalaTokenTypes.tINTERPOLATED_STRING =>
            case _ => result += emptyString //insert empty string between injections
          }
        case _ =>
      }
    }
    result.toSeq
  }
}
