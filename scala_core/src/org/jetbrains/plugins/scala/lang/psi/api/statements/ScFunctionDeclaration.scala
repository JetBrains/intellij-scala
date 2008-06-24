package org.jetbrains.plugins.scala.lang.psi.api.statements

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
* Time: 9:49:23
*/

trait ScFunctionDeclaration extends ScFunction with ScTypedDeclaration {
  def typeElement = returnTypeElement
}