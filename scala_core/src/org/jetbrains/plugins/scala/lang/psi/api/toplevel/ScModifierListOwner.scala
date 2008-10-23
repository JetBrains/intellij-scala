package org.jetbrains.plugins.scala.lang.psi.api.toplevel

import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.base._

/** 
* @author ilyas
*/

trait ScModifierListOwner extends PsiModifierListOwner {

  def getModifierList: ScModifierList

  def hasModifierProperty(name: String) = if (getModifierList != null) getModifierList.hasModifierProperty(name: String) else false

  def setModifierProperty(name: String, value: Boolean) {
    getModifierList.setModifierProperty(name, value)
  }

}