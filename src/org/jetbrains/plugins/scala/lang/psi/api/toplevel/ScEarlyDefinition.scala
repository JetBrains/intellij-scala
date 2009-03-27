package org.jetbrains.plugins.scala.lang.psi.api.toplevel

import psi.ScalaPsiElement
import typedef.ScMember

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

trait ScEarlyDefinitions extends ScalaPsiElement {
  def members() = findChildrenByClass(classOf[ScMember])
}