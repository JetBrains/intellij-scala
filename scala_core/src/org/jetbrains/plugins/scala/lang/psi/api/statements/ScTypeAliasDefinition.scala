package org.jetbrains.plugins.scala.lang.psi.api.statements

import base.types.ScTypeElement
/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
* Time: 9:50:05
*/

trait ScTypeAliasDefinition extends ScTypeAlias {
  def aliasedType = findChildByClass(classOf[ScTypeElement])

  def lowerBound = aliasedType.getType
  def upperBound = aliasedType.getType
}