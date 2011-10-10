package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package patterns

import com.intellij.navigation.NavigationItem
import com.intellij.psi.impl.{ResolveScopeManager, PsiManagerEx}
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScTypedDefinition}
import com.intellij.psi.{PsiClass, PsiElement}
import toplevel.typedef.{ScMember, ScTypeDefinition}
import statements._

trait ScBindingPattern extends ScPattern with ScNamedElement with ScTypedDefinition with NavigationItem {
  override def getTextOffset: Int = nameId.getTextRange.getStartOffset

  def isWildcard: Boolean

  override def getUseScope = {
    val func = PsiTreeUtil.getContextOfType(this, true, classOf[ScFunctionDefinition])
    if (func != null) new LocalSearchScope(func) else getManager.asInstanceOf[PsiManagerEx].getFileManager.getUseScope(this)
  }

  protected def getEnclosingVariable = {
    def goUpper(e: PsiElement): Option[ScVariable] = e match {
      case _ : ScPattern => goUpper(e.getParent)
      case _ : ScPatternArgumentList => goUpper(e.getParent)
      case v: ScVariable => Some(v)
      case _ => None
    }

    goUpper(this)
  }

  override def isStable = getEnclosingVariable match {
    case None => true
    case _ => false
  }
  
  def getContainingClass: PsiClass = {
    ScalaPsiUtil.nameContext(this) match {
      case memb: ScMember => memb.getContainingClass
      case _ => null
    }
  }

  def getOriginalElement: PsiElement = {
    val containingClass = getContainingClass
    if (containingClass == null) return this
    val originalClass: PsiClass = containingClass.getOriginalElement.asInstanceOf[PsiClass]
    if (containingClass eq originalClass) return this
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
            if (nextElem.getName == getName) return nextElem
          }
        case _ =>
      }
    }
    this
  }
}