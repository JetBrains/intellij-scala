package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements

/**
 * @author Jason Zaugg
 */
trait ScMacroDefinition extends ScFunction {
  def typeElement = returnTypeElement
}