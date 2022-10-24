package org.jetbrains.plugins.scala.lang.psi.impl.base
package patterns

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result._

class ScCompositePatternImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScPatternImpl with ScCompositePattern {
  override def isIrrefutableFor(t: Option[ScType]): Boolean = subpatterns.exists(_.isIrrefutableFor(t))

  override def toString: String = "CompositePattern"

  override def `type`(): TypeResult = {
    this.expectedType match {
      case Some(expected) => Right(expected)
      case _              => Failure(ScalaBundle.message("cannot.define.expected.type"))
    }
  }
}