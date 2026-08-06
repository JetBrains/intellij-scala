package org.jetbrains.plugins.scala.lang.psi.api.expr

import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor

trait ScGuard extends ScEnumerator {

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = visitor.visitGuard(this)
}