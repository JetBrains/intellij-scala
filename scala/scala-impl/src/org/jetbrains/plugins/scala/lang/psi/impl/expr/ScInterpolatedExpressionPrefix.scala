package org.jetbrains.plugins.scala.lang.psi.impl.expr

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.base.ScInterpolatedStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement.NameId
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

final class ScInterpolatedExpressionPrefix(node: ASTNode) extends ScReferenceExpressionImpl(node) {

  import ScalaResolveResult.EMPTY_ARRAY

  override def nameId: NameId.Name = new NameId.Name(this)

  override def toString = s"InterpolatedExpressionPrefix: $getText"

  override def multiResolveScala(incomplete: Boolean): Array[ScalaResolveResult] = getParent match {
    case literal: ScInterpolatedStringLiteral =>
      literal.desugaredExpression.fold(EMPTY_ARRAY) {
        case (reference: ScReferenceExpression, _) => reference.multiResolveScala(incomplete)
      }
    case _ => EMPTY_ARRAY
  }
}
