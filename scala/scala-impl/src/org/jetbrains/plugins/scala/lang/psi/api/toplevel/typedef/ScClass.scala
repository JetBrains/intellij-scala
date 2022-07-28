package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package typedef

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction

trait ScClass extends ScTypeDefinition with ScDerivesClauseOwner with ScConstructorOwner {

  def typeParamString: String = typeParameters
    .map(ScalaPsiUtil.typeParamString(_)) match {
    case Seq() => ""
    case seq => seq.mkString("[", ", ", "]")
  }

  def tooBigForUnapply: Boolean = constructor.exists(_.parameters.length > 22)

  def getSyntheticImplicitMethod: Option[ScFunction]
}
