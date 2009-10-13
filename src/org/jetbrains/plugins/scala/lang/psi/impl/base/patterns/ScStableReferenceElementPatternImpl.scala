package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package patterns

import _root_.org.jetbrains.plugins.scala.lang.psi.types.ScType
import api.base.patterns.ScStableReferenceElementPattern
import api.expr.{ScReferenceExpression, ScExpression}
import com.intellij.lang.ASTNode

/**
 * @author ilyas
 */

class ScStableReferenceElementPatternImpl(node : ASTNode) extends ScalaPsiElementImpl(node) with ScStableReferenceElementPattern {

  override def toString: String = "StableElementPattern"

  override def calcType: ScType = getReferenceExpression match {
    case Some(e) => e.getType
    case None => org.jetbrains.plugins.scala.lang.psi.types.Nothing
  }
}