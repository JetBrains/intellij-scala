package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package patterns

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScStableReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult

final class ScStableReferencePatternImpl(node: ASTNode,
                                         override val toString: String)
  extends ScalaPsiElementImpl(node)
    with ScPatternImpl
    with ScStableReferencePattern {

  override def referenceExpression: Option[ScReferenceExpression] =
    Option(findChildByClass(classOf[ScReferenceExpression]))

  override def isIrrefutableFor(t: Option[ScType]): Boolean = false

  override def `type`(): TypeResult = this.flatMapType(referenceExpression)
}