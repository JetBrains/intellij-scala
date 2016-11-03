package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements

import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScArguments

/**
 * @author Jason Zaugg
 */
trait ScMacroDefinition extends ScFunction {
  def typeElement = returnTypeElement
  def body: Option[ScExpression]
  def expand(args: Seq[ScExpression]): ScalaPsiElement
}