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
import com.intellij.psi.stubs.StubElement
import templates.{ScExtendsBlock, ScTemplateBody}
import com.intellij.psi.impl.source.PsiFileImpl
import collection.mutable.ArrayBuffer
import base.ScPrimaryConstructor
import statements.params.ScClassParameter
import statements.ScFunction
import psi.stubs.ScMemberOrLocal
import com.intellij.psi.search.{LocalSearchScope, SearchScope}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlock

/**
  * @author Alexander Podkhalyuzin
  * Date: 04.05.2008
  */
trait ScMember extends ScalaPsiElement with ScModifierListOwner with PsiMember {
  def getContainingClass: PsiClass = containingClass

  def isPrivate: Boolean = hasModifierPropertyScala("private")

  def isProtected: Boolean = hasModifierPropertyScala("protected")

  def isPublic: Boolean = !isPrivate && !isProtected

  def isInstance: Boolean = !isLocal

  /**
    * getContainingClassStrict(bar) == null in
    *
    * `object a { def foo { def bar = 0 }}`
    */
  def containingClass: ScTemplateDefinition = {
    val stub: StubElement[_ <: PsiElement] = this match {
      case file: PsiFileImpl => file.getStub
      case st: ScalaStubBasedElementImpl[_] => st.getStub
      case _ => null
    }
    stub match {
      case m: ScMemberOrLocal if m.isLocal => return null
      case _ =>
    }
    val context = getContext
    (getContainingClassLoose, this) match {
      case (null, _) => null
      case (found, fun: ScFunction) if fun.isSynthetic => found
      case (found, _: ScClassParameter | _: ScPrimaryConstructor) => found
      case (found, _) if context == found.extendsBlock || Some(context) == found.extendsBlock.templateBody ||
        Some(context) == found.extendsBlock.earlyDefinitions => found
      case (found, _) => null // See SCL-3178
    }
  }

  def getContainingClassLoose: ScTemplateDefinition = {
    val stub: StubElement[_ <: PsiElement] = this match {
      case file: PsiFileImpl => file.getStub
      case st: ScalaStubBasedElementImpl[_] => st.getStub
      case _ => null
    }
    if (stub != null) {
      stub.getParentStubOfType(classOf[ScTemplateDefinition])
    } else {
      child match {
        // TODO is all of this mess still necessary?! 
        case c: ScClass if c.isCase => {
          this match {
            case fun: ScFunction if fun.isSyntheticApply || fun.isSyntheticUnapply ||
              fun.isSyntheticUnapplySeq =>
              //this is special case for synthetic apply and unapply methods
              ScalaPsiUtil.getCompanionModule(c) match {
                case Some(td) => return td
                case _ =>
              }
            case _ =>
          }
        }
        case _ =>
      }
      PsiTreeUtil.getContextOfType(this, true, classOf[ScTemplateDefinition])
    }
  }

  def isLocal: Boolean = {
    val stub: StubElement[_ <: PsiElement] = this match {
      case file: PsiFileImpl => file.getStub
      case st: ScalaStubBasedElementImpl[_] => st.getStub
      case _ => null
    }
    stub match {
      case memberOrLocal: ScMemberOrLocal =>
        return memberOrLocal.isLocal
      case _ =>
    }
    containingClass == null
  }

  override def hasModifierProperty(name: String) = {
    if (name == PsiModifier.PUBLIC) {
      !hasModifierProperty("private") && !hasModifierProperty("protected")
    } else if (name == PsiModifier.STATIC) {
      containingClass.isInstanceOf[ScObject]
    } else super.hasModifierProperty(name)
  }

  protected def isSimilarMemberForNavigation(m: ScMember, isStrict: Boolean) = false

  override def getNavigationElement: PsiElement = getContainingFile match {
    case s: ScalaFileImpl if s.isCompiled => getSourceMirrorMember
    case _ => this
  }

  private def getSourceMirrorMember: ScMember = getParent match {
    case tdb: ScTemplateBody => tdb.getParent match {
      case eb: ScExtendsBlock => eb.getParent match {
        case td: ScTypeDefinition => td.getNavigationElement match {
          case c: ScTypeDefinition => {
            val membersIterator = c.members.iterator
            val buf: ArrayBuffer[ScMember] = new ArrayBuffer[ScMember]
            while (membersIterator.hasNext) {
              val member = membersIterator.next()
              if (isSimilarMemberForNavigation(member, isStrict = false)) buf += member
            }
            if (buf.length == 0) this
            else if (buf.length == 1) buf(0)
            else {
              val filter = buf.filter(isSimilarMemberForNavigation(_, isStrict = true))
              if (filter.length == 0) buf(0)
              else filter(0)
            }
          }
          case _ => this
        }
        case _ => this
      }
      case _ => this
    }
    case c: ScTypeDefinition if this.isInstanceOf[ScPrimaryConstructor] => //primary constructor
      c.getNavigationElement match {
        case td: ScClass =>
          td.constructor match {
            case Some(constr) => constr
            case _ => this
          }
        case _ => this
      }
    case _ => this
  }

  abstract override def getUseScope: SearchScope = {
    ScalaPsiUtil.intersectScopes(super.getUseScope, this match {
      case m if m.getModifierList.accessModifier.exists(mod => mod.isPrivate && mod.isThis) =>
        Option(m.containingClass).map(new LocalSearchScope(_))
      case m if m.getModifierList.accessModifier.exists(_.isUnqualifiedPrivateOrThis) =>
        Option(m.containingClass).map(ScalaPsiUtil.withCompanionSearchScope)
      case _ =>
        val blockOrMember = PsiTreeUtil.getContextOfType(this, true, classOf[ScBlock], classOf[ScMember])
        blockOrMember match {
          case null => None
          case block: ScBlock => Some(new LocalSearchScope(block))
          case member: ScMember => Some(member.getUseScope)
        }
    })
  }
}