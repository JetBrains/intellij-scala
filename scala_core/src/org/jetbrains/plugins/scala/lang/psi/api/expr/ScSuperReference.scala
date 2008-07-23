package org.jetbrains.plugins.scala.lang.psi.api.expr

import com.intellij.psi.PsiClass
import base.ScPathElement
import psi.ScalaPsiElement

/** 
* @author Alexander Podkhalyuzin
* Date: 14.03.2008
*/

trait ScSuperReference extends ScExpression with ScPathElement { //todo extract a separate 'this' path element
  def refClass : Option[PsiClass] = None
}