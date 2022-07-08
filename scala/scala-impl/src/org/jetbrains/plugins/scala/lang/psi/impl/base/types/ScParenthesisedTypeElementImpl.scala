package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package types

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.Unit
import org.jetbrains.plugins.scala.lang.psi.types.result._

class ScParenthesisedTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScParenthesisedTypeElement {
  override protected def innerType: TypeResult = innerElement match {
    case Some(el) => el.`type`()
    case None => Right(Unit)
  }

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitParenthesisedTypeElement(this)
  }
}