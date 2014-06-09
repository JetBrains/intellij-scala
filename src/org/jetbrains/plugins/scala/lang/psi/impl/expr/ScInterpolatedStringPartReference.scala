package org.jetbrains.plugins.scala
package lang.psi.impl.expr

import com.intellij.lang.ASTNode
import com.intellij.psi.{PsiPolyVariantReference, ResolveResult, PsiElement}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScInterpolatedStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction

/**
 * @author kfeodorov 
 * @since 15.03.14.
 */
class ScInterpolatedStringPartReference(node: ASTNode) extends ScReferenceExpressionImpl(node) {
  override def nameId: PsiElement = this
  override def toString = s"InterpolatedStringPartReference: $getText"

  override def multiResolve(incomplete: Boolean): Array[ResolveResult]  = {
    val parent = getParent match {
      case p: ScInterpolatedStringLiteral => p
      case _ => return Array[ResolveResult]()
    }

    parent.getStringContextExpression match {
      case Some(expr) => expr.getFirstChild.getLastChild.findReferenceAt(0) match {
        case ref: PsiPolyVariantReference =>
          val resolve1 = ref.multiResolve(incomplete)
          resolve1.filter(_.getElement.isInstanceOf[ScFunction])
        case _ => Array[ResolveResult]()
      }
      case _ => Array[ResolveResult]()
    }
  }
}
