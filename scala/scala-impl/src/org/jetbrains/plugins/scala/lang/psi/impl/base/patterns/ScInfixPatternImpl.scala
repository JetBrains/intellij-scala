package org.jetbrains.plugins.scala.lang.psi.impl.base
package patterns

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result._

class ScInfixPatternImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScPatternImpl with ScInfixPattern {
  override def isIrrefutableFor(t: Option[ScType]): Boolean =
    ScConstructorPatternImpl.isIrrefutable(t, operation, left :: rightOption.toList)

  override def toString: String = "InfixPattern"

  override def `type`(): TypeResult = {
    this.expectedType match {
      case Some(x) => Right(x)
      case _ => Failure(ScalaBundle.message("cannot.define.expected.type"))
    }
  }
}