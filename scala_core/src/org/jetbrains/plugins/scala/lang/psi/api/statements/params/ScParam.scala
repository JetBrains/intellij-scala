package org.jetbrains.plugins.scala.lang.psi.api.statements.params

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

trait ScParam extends ScNamedElement {
  def getTypeNode: ScalaPsiElement
}