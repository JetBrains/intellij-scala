package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package patterns

import com.intellij.navigation.NavigationItem
import com.intellij.psi.impl.ResolveScopeManager
import com.intellij.psi.search.{SearchScope, LocalSearchScope}
import com.intellij.psi.util.PsiTreeUtil
import statements._
import toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScEarlyDefinitions, ScNamedElement, ScTypedDefinition}
import toplevel.typedef.{ScTemplateDefinition, ScMember, ScTypeDefinition}
import com.intellij.psi.javadoc.PsiDocComment
import extensions.toPsiNamedElementExt
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScForStatement, ScGenerator, ScEnumerator, ScBlock}

trait ScBindingPattern extends ScPattern with ScNamedElement with ScTypedDefinition with NavigationItem with PsiDocCommentOwner {
  override def getTextOffset: Int = nameId.getTextRange.getStartOffset

  def isWildcard: Boolean

  protected def getEnclosingVariable: Option[ScVariable] = {
    ScalaPsiUtil.nameContext(this) match {
      case v: ScVariable => Some(v)
      case _ => None
    }
  }

  override def isStable = getEnclosingVariable match {
    case None => true
    case _ => false
  }

  override def isVar: Boolean = nameContext.isInstanceOf[ScVariable]
  override def isVal: Boolean = nameContext.isInstanceOf[ScValue]

  def isClassMember = nameContext.getContext match {
    case _: ScTemplateBody | _: ScEarlyDefinitions => true
    case _ => false
  }
  def isBeanProperty: Boolean = nameContext match {
    case a: ScAnnotationsHolder => ScalaPsiUtil.isBeanProperty(a)
    case _ => false
  }

  def containingClass: ScTemplateDefinition = {
    ScalaPsiUtil.nameContext(this) match {
      case memb: ScMember => memb.containingClass
      case _ => null
    }
  }


  def getOriginalElement: PsiElement = {
    val ccontainingClass = containingClass
    if (ccontainingClass == null) return this
    val originalClass: PsiClass = ccontainingClass.getOriginalElement.asInstanceOf[PsiClass]
    if (ccontainingClass eq originalClass) return this
    if (!originalClass.isInstanceOf[ScTypeDefinition]) return this
    val c = originalClass.asInstanceOf[ScTypeDefinition]
    val membersIterator = c.members.iterator
    while (membersIterator.hasNext) {
      val member = membersIterator.next()
      member match {
        case _: ScValue | _: ScVariable =>
          val d = member.asInstanceOf[ScDeclaredElementsHolder]
          val elemsIterator = d.declaredElements.iterator
          while (elemsIterator.hasNext) {
            val nextElem = elemsIterator.next()
            if (nextElem.name == name) return nextElem
          }
        case _ =>
      }
    }
    this
  }

  def getDocComment: PsiDocComment = {
    nameContext match {
      case d: PsiDocCommentOwner => d.getDocComment
      case _ => null
    }
  }

  def isDeprecated: Boolean = {
    nameContext match {
      case d: PsiDocCommentOwner => d.isDeprecated
      case _ => false
    }
  }

  /**
   * It's for Java only
   */
  def getContainingClass: PsiClass = {
    nameContext match {
      case m: PsiMember => m.getContainingClass
      case _ => null
    }
  }

  def getModifierList: PsiModifierList = {
    nameContext match {
      case owner: PsiModifierListOwner => owner.getModifierList
      case _ => null
    }
  }

  def hasModifierProperty(name: String): Boolean = {
    nameContext match {
      case owner: PsiModifierListOwner => owner.hasModifierProperty(name)
      case _ => false
    }
  }
}