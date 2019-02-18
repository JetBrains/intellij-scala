package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package patterns

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScStableReferencePattern
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult

/**
 * @author ilyas
 */

class ScStableReferencePatternImpl(node : ASTNode) extends ScalaPsiElementImpl(node) with ScPatternImpl with ScStableReferencePattern {
  override def isIrrefutableFor(t: Option[ScType]): Boolean = false

  override def toString: String = "StableElementPattern"

  override def `type`(): TypeResult = this.flatMapType(referenceExpression)
}