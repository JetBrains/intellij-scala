package org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef

import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import com.intellij.psi.util._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._

/**
* @author Alexander Podkhalyuzin
* Date: 04.05.2008
*/

trait ScMember extends ScalaPsiElement with ScModifierListOwner with PsiMember {

  def getContainingClass = PsiTreeUtil.getParentOfType(this, classOf[ScTypeDefinition])

}