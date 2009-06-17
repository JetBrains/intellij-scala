package org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef

import base.ScModifierList
import impl.ScalaFileImpl
import impl.toplevel.typedef.ScTypeDefinitionImpl
import lexer.ScalaTokenTypes
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import com.intellij.psi.util._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import statements.ScFunction
import templates.{ScExtendsBlock, ScTemplateBody}

/**
 * @author Alexander Podkhalyuzin
 * Date: 04.05.2008
 */

trait ScMember extends ScalaPsiElement with ScModifierListOwner with PsiMember {
  def getContainingClass: ScTemplateDefinition = PsiTreeUtil.getParentOfType(this, classOf[ScTemplateDefinition])

  override def hasModifierProperty(name: String) = {
    if (name == PsiModifier.STATIC) {
      getContainingClass match {
        case obj: ScObject => true
        case _ => false
      }
    } else if (name == PsiModifier.PUBLIC) {
      !hasModifierProperty("private") && !hasModifierProperty("protected")
    } else super.hasModifierProperty(name)
  }

  protected def findSameMemberInSource(m: ScMember) = false

  override def getNavigationElement: PsiElement = getContainingFile match {
    case s: ScalaFileImpl if s.isCompiled => getSourceMirrorMember
    case _ => this
  }

  private def getSourceMirrorMember = getParent match {
    case tdb: ScTemplateBody => tdb.getParent match {
      case eb: ScExtendsBlock => eb.getParent match {
        case td: ScTypeDefinition => td.getNavigationElement match {
          case c: ScTypeDefinition => c.members.find(findSameMemberInSource _) match {
            case Some(m) => m
            case None => this
          }
          case _ => this
        }
        case _ => this
      }
      case _ => this
    }
    case _ => this
  }


}