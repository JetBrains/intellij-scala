package org.jetbrains.plugins.scala.lang.psi.api.statements

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScIdList

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
* Time: 9:44:29
*/

trait ScValueDeclaration extends ScValue with ScDeclaration {
  def getIdList = findChildByClass(classOf[ScIdList])
}