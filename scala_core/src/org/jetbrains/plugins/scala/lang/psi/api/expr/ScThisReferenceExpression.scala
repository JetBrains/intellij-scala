package org.jetbrains.plugins.scala.lang.psi.api.expr

import toplevel.typedef.ScTypeDefinition
import base.ScPathElement
import psi.ScalaPsiElement

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

trait ScThisReference extends ScExpression with ScPathElement { //todo extract a separate 'this' path element
  def refClass : Option[ScTypeDefinition] = None //todo
}