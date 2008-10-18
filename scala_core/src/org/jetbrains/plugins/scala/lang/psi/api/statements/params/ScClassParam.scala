package org.jetbrains.plugins.scala.lang.psi.api.statements.params

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

trait ScClassParameter extends ScParameter {
  def isVal() : Boolean
  def isVar() : Boolean
}