package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements

import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
* Time: 9:49:23
*/

trait ScFunctionDeclaration extends ScFunction with ScTypedDeclaration {
  def typeElement: Option[ScTypeElement] = returnTypeElement
}