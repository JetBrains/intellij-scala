package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

/**
  * @author Alexander.Podkhalyuzin
  */
trait ScConstrBlock extends ScBlockExpr {

  def selfInvocation: Option[ScSelfInvocation] = findChild(classOf[ScSelfInvocation])

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitConstrBlock(this)
  }
}