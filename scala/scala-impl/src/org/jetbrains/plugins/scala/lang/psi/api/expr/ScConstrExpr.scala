package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import org.jetbrains.plugins.scala.lang.psi.api._


trait ScConstrExprBase extends ScExpressionBase { this: ScConstrExpr =>
  def selfInvocation: Option[ScSelfInvocation]
}