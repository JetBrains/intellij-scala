package org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef

import base.ScModifierList
import lexer.ScalaTokenTypes
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import com.intellij.psi.util._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._

/**
* @author Alexander Podkhalyuzin
* Date: 04.05.2008
*/

trait ScMember extends ScalaPsiElement with ScModifierListOwner with PsiMember {
  def getModifierList = findChildByClass(classOf[ScModifierList])

  def getContainingClass = PsiTreeUtil.getParentOfType(this, classOf[ScTemplateDefinition])
  override def hasModifierProperty(name: String) = {
    if (name == PsiModifier.STATIC) {
      getContainingClass match {
        case obj : ScObject => true
        case _ =>  false
      }
    } else if (name == PsiModifier.PUBLIC) {
      val list = getModifierList
      !list.has(ScalaTokenTypes.kPRIVATE) && !list.has(ScalaTokenTypes.kPROTECTED)
    } else super.hasModifierProperty(name)
  }
}