package org.jetbrains.plugins.scala.lang.psi.api.statements

trait ScFunctionDeclaration extends ScFunction with ScTypedDeclaration {
  override final def isEffectivelyFinal: Boolean = false
}