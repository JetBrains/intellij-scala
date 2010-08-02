package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements
package params

import base.ScModifierList
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import toplevel.ScModifierListOwner
import toplevel.typedef.ScMember

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

trait ScClassParameter extends ScParameter with ScModifierListOwner with ScMember {
  def isVal() : Boolean
  def isVar() : Boolean
}