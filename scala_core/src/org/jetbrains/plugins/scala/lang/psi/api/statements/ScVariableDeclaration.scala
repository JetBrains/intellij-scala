package org.jetbrains.plugins.scala.lang.psi.api.statements


import org.jetbrains.plugins.scala.lang.psi.api.base.ScIdList
/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
* Time: 9:50:20
*/

trait ScVariableDeclaration extends ScVariable {

  /**
  * @return non-null identifier list
  */
  def getIdList: ScIdList

}