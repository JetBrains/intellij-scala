package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements


import base.ScIdList
import api.base.types.ScTypeElement
import toplevel.ScTypedDefinition
import psi.types.Nothing
/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
* Time: 9:50:20
*/

trait ScVariableDeclaration extends ScVariable with ScTypedDeclaration {
  def getIdList: ScIdList
  def declaredElements : Seq[ScTypedDefinition]
  def getType = typeElement match {
    case Some(te) => te.cachedType
    case None => Nothing
  }
}