package org.jetbrains.plugins.scala.lang.psi.api.statements


import base.ScIdList
import api.base.types.ScTypeElement
import toplevel.ScTyped
/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
* Time: 9:50:20
*/

trait ScVariableDeclaration extends ScVariable with ScTypedDeclaration {
  def getIdList: ScIdList = findChildByClass(classOf[ScIdList])
  def declaredElements : Seq[ScTyped]
}