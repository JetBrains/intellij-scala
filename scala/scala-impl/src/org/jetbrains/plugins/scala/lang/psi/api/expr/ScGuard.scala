package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import org.jetbrains.plugins.scala.lang.psi.api._


trait ScGuardBase extends ScEnumeratorBase { this: ScGuard =>

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = visitor.visitGuard(this)
}