package org.jetbrains.plugins.scala
package lang.psi.impl.expr

import com.intellij.lang.ASTNode
import com.intellij.psi.{PsiElement, PsiPolyVariantReference, ResolveResult}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolatedStringLiteral, ScReferenceElement}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

/**
 * @author kfeodorov 
 * @since 15.03.14.
 */
class ScInterpolatedStringPartReference(node: ASTNode) extends ScReferenceExpressionImpl(node) {
  override def nameId: PsiElement = this
  override def toString = s"InterpolatedStringPartReference: $getText"

  override def multiResolveScala(incomplete: Boolean): Array[ScalaResolveResult]  = {
    val parent = getParent match {
      case p: ScInterpolatedStringLiteral => p
      case _ => return ScalaResolveResult.EMPTY_ARRAY
    }

    parent.getStringContextExpression match {
      case Some(expr) => expr.getFirstChild.getLastChild.findReferenceAt(0) match {
        case ref: ScReferenceElement => ref.multiResolveScala(incomplete)
        case _ => ScalaResolveResult.EMPTY_ARRAY
      }
      case _ => ScalaResolveResult.EMPTY_ARRAY
    }
  }
}
