package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements

import icons.Icons
import javax.swing.Icon
import psi.ScalaPsiElement
import toplevel.ScPolymorphicElement
import types.ScType
import base.types.ScExistentialClause
import lexer.ScalaTokenTypes
import com.intellij.psi.{PsiClass, PsiElement, PsiDocCommentOwner}
import toplevel.typedef.{ScTypeDefinition, ScDocCommentOwner, ScMember}

/**
 * @author Alexander Podkhalyuzin
 * Date: 22.02.2008
 * Time: 9:46:00
 */

trait ScTypeAlias extends ScPolymorphicElement with ScMember with ScAnnotationsHolder with ScDocCommentOwner {
  override def getIcon(flags: Int): Icon = Icons.TYPE_ALIAS

  override protected def isSimilarMemberForNavigation(m: ScMember, isStrict: Boolean) = m match {
    case t: ScTypeAlias => t.name == name
    case _ => false
  }

  def isExistentialTypeAlias: Boolean = {
    getContext match {
      case _: ScExistentialClause => true
      case _ => false
    }
  }

  override def isDeprecated =
    hasAnnotation("scala.deprecated") != None || hasAnnotation("java.lang.Deprecated") != None

  def getTypeToken: PsiElement = findFirstChildByType(ScalaTokenTypes.kTYPE)

  def getOriginalElement: PsiElement = {
    val containingClass = getContainingClass
    if (containingClass == null) return this
    val originalClass: PsiClass = containingClass.getOriginalElement.asInstanceOf[PsiClass]
    if (containingClass eq  originalClass) return this
    if (!originalClass.isInstanceOf[ScTypeDefinition]) return this
    val c = originalClass.asInstanceOf[ScTypeDefinition]
    val aliasesIterator = c.aliases.iterator
    while (aliasesIterator.hasNext) {
      val alias = aliasesIterator.next()
      if (alias.name == name) return alias
    }
    this
  }
}