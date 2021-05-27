package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package patterns

import com.intellij.navigation.NavigationItem
import com.intellij.psi._
import javax.swing.Icon
import com.intellij.psi.javadoc.PsiDocComment
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTemplateDefinition, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScEarlyDefinitions, ScNamedElement, ScTypedDefinition}

trait ScBindingPattern extends ScPattern with ScNamedElement with ScTypedDefinition with NavigationItem with PsiDocCommentOwner {
  override def getTextOffset: Int = nameId.getTextRange.getStartOffset

  def isWildcard: Boolean = name == "_"

  override def isStable: Boolean = !isVar

  override def isVar: Boolean = nameContext.is[ScVariable]

  override def isVal: Boolean = nameContext.is[ScValue]

  def isClassMember: Boolean = nameContext.getContext match {
    case _: ScTemplateBody | _: ScEarlyDefinitions => true
    case _ => false
  }

  def isBeanProperty: Boolean = nameContext match {
    case a: ScAnnotationsHolder => PropertyMethods.isBeanProperty(a)
    case _ => false
  }

  def containingClass: ScTemplateDefinition = nameContext match {
    case memb: ScMember => memb.containingClass
    case _ => null
  }

  override def getOriginalElement: PsiElement = {
    val ccontainingClass = containingClass
    if (ccontainingClass == null) return this
    val originalClass: PsiClass = ccontainingClass.getOriginalElement.asInstanceOf[PsiClass]
    if (ccontainingClass eq originalClass) return this
    if (!originalClass.is[ScTypeDefinition]) return this
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

  override def getDocComment: PsiDocComment = {
    nameContext match {
      case d: PsiDocCommentOwner => d.getDocComment
      case _ => null
    }
  }

  override def isDeprecated: Boolean = {
    nameContext match {
      case d: PsiDocCommentOwner => d.isDeprecated
      case _ => false
    }
  }

  override def getIcon(flags: Int): Icon = Icons.PATTERN_VAL

  /**
   * It's for Java only
   */
  override def getContainingClass: PsiClass = {
    nameContext match {
      case m: PsiMember => m.getContainingClass
      case _ => null
    }
  }

  override def getModifierList: PsiModifierList = {
    nameContext match {
      case owner: PsiModifierListOwner => owner.getModifierList
      case _ => null
    }
  }

  override def hasModifierProperty(name: String): Boolean = {
    nameContext match {
      case owner: PsiModifierListOwner => owner.hasModifierProperty(name)
      case _ => false
    }
  }
}
