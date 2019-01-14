package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScSequenceArg, ScTypeElement}

/**
  * @author Alexander Podkhalyuzin
  *         Date: 06.03.2008
  */
trait ScTypedExpression extends ScExpression {
  def expr: ScExpression = findChildByClassScala(classOf[ScExpression])

  def typeElement: Option[ScTypeElement] = findChild(classOf[ScTypeElement])

  def isSequenceArg: Boolean = getLastChild.isInstanceOf[ScSequenceArg]

  override def accept(visitor: ScalaElementVisitor): Unit = {
    visitor.visitTypedStmt(this)
  }
}