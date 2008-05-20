package org.jetbrains.plugins.scala.lang.psi.api.toplevel

import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.base._

/** 
* @author ilyas
*/

trait ScModifierListOwner extends PsiModifierListOwner {

  def getModifierList: ScModifierList

  def hasModifierProperty(name:String) = getModifierList.hasModifierProperty(name:String)

}