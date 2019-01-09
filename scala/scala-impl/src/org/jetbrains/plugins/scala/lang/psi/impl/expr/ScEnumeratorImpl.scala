package org.jetbrains.plugins.scala.lang.psi.impl.expr

import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScEnumerator

trait ScEnumeratorImpl extends ScEnumerator {

  override def forStatement: Option[ScForStatementImpl] = this.parentOfType(classOf[ScForStatementImpl])

  override def analog: Option[ScEnumerator.Analog] = forStatement flatMap {
    _.getDesugaredEnumeratorAnalog(this)
  }
}
