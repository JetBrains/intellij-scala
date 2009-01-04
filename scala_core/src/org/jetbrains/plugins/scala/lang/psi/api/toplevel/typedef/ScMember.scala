package org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef

import base.ScModifierList
import impl.toplevel.typedef.ScTypeDefinitionImpl
import lexer.ScalaTokenTypes
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import com.intellij.psi.util._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import statements.ScFunction

/**
* @author Alexander Podkhalyuzin
* Date: 04.05.2008
*/

trait ScMember extends ScalaPsiElement with ScModifierListOwner with PsiMember {
  def getModifierList = findChildByClass(classOf[ScModifierList])

  def getContainingClass: ScTemplateDefinition = PsiTreeUtil.getParentOfType(this, classOf[ScTemplateDefinition])
  override def hasModifierProperty(name: String) = {
    if (name == PsiModifier.STATIC) {
      getContainingClass match {
        case obj : ScObject => true
        case _ : ScTrait  | _ : ScClass => this match { //dirty hack for runnable objects, inherited traits with 'main' methos
          case f: ScFunction => f.name == "main" && {
            val params = f.getParameterList.getParameters
            params.length == 1 && (params(0).getType match {
              case at: PsiArrayType => at.getComponentType.equalsToText("java.lang.String")
              case _ => false
            })
          }
          case _ => false
        }
        case _ =>  false
      }
    } else if (name == PsiModifier.PUBLIC) {
      val list = getModifierList
      !list.has(ScalaTokenTypes.kPRIVATE) && !list.has(ScalaTokenTypes.kPROTECTED)
    } else super.hasModifierProperty(name)
  }
}