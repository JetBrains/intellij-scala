package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import org.jetbrains.plugins.scala.lang.psi.api._


/**
  * @author Alexander.Podkhalyuzin
  */
trait ScConstrBlockBase extends ScBlockExprBase { this: ScConstrBlock =>

  def selfInvocation: Option[ScSelfInvocation] = findChild[ScSelfInvocation]

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitConstrBlock(this)
  }
}