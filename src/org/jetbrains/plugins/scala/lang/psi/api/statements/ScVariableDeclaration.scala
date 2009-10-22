package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements
import base.ScIdList
import toplevel.ScTypedDefinition
import psi.types.Nothing
import org.jetbrains.plugins.scala.lang.psi.types.Any

/**
* @author Alexander Podkhalyuzin
*/

trait ScVariableDeclaration extends ScVariable with ScTypedDeclaration {
  def getIdList: ScIdList
  def declaredElements : Seq[ScTypedDefinition]
}