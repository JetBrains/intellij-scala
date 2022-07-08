package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package types

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult
import org.jetbrains.plugins.scala.lang.psi.types.{ScExistentialArgument, ScExistentialType}

class ScWildcardTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScTypeBoundsOwnerImpl with ScWildcardTypeElement {
  override protected def innerType: TypeResult = {
    for {
      lb <- lowerBound
      ub <- upperBound
    } yield {
      ScExistentialType(ScExistentialArgument("_$1", Nil, lb, ub))
    }
  }

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitWildcardTypeElement(this)
  }
}