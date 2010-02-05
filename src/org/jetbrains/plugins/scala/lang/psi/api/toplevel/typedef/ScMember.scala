package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package typedef

import impl.ScalaFileImpl
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import com.intellij.psi.util._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import stubs.StubElement
import templates.{ScExtendsBlock, ScTemplateBody}
import impl.source.PsiFileImpl

/**
 * @author Alexander Podkhalyuzin
 * Date: 04.05.2008
 */

trait ScMember extends ScalaPsiElement with ScModifierListOwner with PsiMember {
  def getContainingClass: ScTemplateDefinition = {
    val stub: StubElement[_ <: PsiElement] = this match {
      case file: PsiFileImpl => file.getStub
      case st: ScalaStubBasedElementImpl[_] => st.getStub
      case _ => null
    }
    if (stub != null) {
      stub.getParentStubOfType(classOf[ScTemplateDefinition])
    } else PsiTreeUtil.getParentOfType(this, classOf[ScTemplateDefinition])
  }

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

  protected def isSimilarMemberForNavigation(m: ScMember) = false

  override def getNavigationElement: PsiElement = getContainingFile match {
    case s: ScalaFileImpl if s.isCompiled => getSourceMirrorMember
    case _ => this
  }

  private def getSourceMirrorMember = getParent match {
    case tdb: ScTemplateBody => tdb.getParent match {
      case eb: ScExtendsBlock => eb.getParent match {
        case td: ScTypeDefinition => td.getNavigationElement match {
          case c: ScTypeDefinition => c.members.find(isSimilarMemberForNavigation _) match {
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