package org.jetbrains.plugins.scala.lang.psi.impl.base
package patterns

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult

class ScLiteralPatternImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScPatternImpl with ScLiteralPattern {
  override def isIrrefutableForImpl(scrutineeType: ScType, deep: Boolean): Boolean =
    scrutineeType.conforms(getLiteral.literalType)

  override def toString: String = "LiteralPattern"

  override def `type`(): TypeResult = getLiteral.`type`()
}