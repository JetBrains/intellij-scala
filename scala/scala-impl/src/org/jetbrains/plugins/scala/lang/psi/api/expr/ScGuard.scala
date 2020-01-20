package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

trait ScGuard extends ScEnumerator {

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = visitor.visitGuard(this)
}