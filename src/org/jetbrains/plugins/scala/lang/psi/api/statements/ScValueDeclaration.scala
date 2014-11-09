package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements

import org.jetbrains.plugins.scala.lang.psi.api.base.ScIdList
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
* Time: 9:44:29
*/

trait ScValueDeclaration extends ScValue with ScTypedDeclaration {
  def getIdList: ScIdList
  def declaredElements : Seq[ScTypedDefinition]
}