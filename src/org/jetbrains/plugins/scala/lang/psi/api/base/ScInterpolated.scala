package org.jetbrains.plugins.scala
package lang.psi.api.base

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScReferenceExpression, ScBlockExpr, ScExpression}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.caches.CachesUtil
import com.intellij.psi.util.PsiModificationTracker
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScInterpolatedPrefixReference
import scala.collection.mutable.ListBuffer
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes

/**
 * @author kfeodorov
 * @since 03.03.14.
 */
trait ScInterpolated extends ScLiteral with ScalaPsiElement {

  val node: ASTNode

  def getStringContextExpression: Option[ScExpression] = {
    def getExpandedExprBuilder(l: ScInterpolated) = {
      val prefix = getFirstChild.getText
      val parts = getStringParts(l).mkString("\"", "\", \"", "\"") //making list of string literals
      val params = l.getInjections.map(_.getText).mkString("(", ",", ")")
      val expr = s"StringContext($parts).${prefix}$params"
      Option(ScalaPsiElementFactory.createExpressionWithContextFromText(expr, node.getPsi.getContext, node.getPsi))
    }

    CachesUtil.get(this, CachesUtil.STRING_CONTEXT_EXPANDED_EXPR_KEY,
      new CachesUtil.MyProvider[ScInterpolated, Option[ScExpression]](this, getExpandedExprBuilder)(PsiModificationTracker.MODIFICATION_COUNT))
  }

  def getInjections: Array[ScExpression] = {
    getNode.getChildren(null).flatMap { _.getPsi match {
      case a: ScBlockExpr => Array[ScExpression](a)
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
          child.getText.toCharArray match {
            case Array('"', '"', '"', _) => result += child.getText.substring(3)
            case Array(_) => result += child.getText
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
