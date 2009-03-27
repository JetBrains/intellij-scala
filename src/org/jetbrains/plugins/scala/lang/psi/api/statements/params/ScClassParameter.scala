package org.jetbrains.plugins.scala.lang.psi.api.statements.params

import base.ScModifierList
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import toplevel.ScModifierListOwner

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

trait ScClassParameter extends ScParameter with ScModifierListOwner {
  override def hasModifierProperty(name: String) = super[ScModifierListOwner].hasModifierProperty(name)

  def isVal() : Boolean
  def isVar() : Boolean
}