package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
* Time: 9:49:23
*/

trait ScFunctionDeclaration extends ScFunction with ScTypedDeclaration {
  def typeElement = returnTypeElement
}