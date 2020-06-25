package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements
import org.jetbrains.plugins.scala.lang.psi.api.base.ScIdList
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition

/**
* @author Alexander Podkhalyuzin
*/

trait ScVariableDeclaration extends ScVariable with ScTypedDeclaration {
  def getIdList: ScIdList
  override def declaredElements : Seq[ScTypedDefinition]
  override def isAbstract: Boolean = true
}