package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements

import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement

/**
 * @author Jason Zaugg
 */
trait ScMacroDefinition extends ScFunction {
  def typeElement: Option[ScTypeElement] = returnTypeElement
}