package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements

import com.intellij.psi.{PsiClass, PsiElement}
import javax.swing.Icon
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScExistentialClause
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPolymorphicElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScDocCommentOwner, ScMember, ScTypeDefinition}

/**
 * @author Alexander Podkhalyuzin
 * Date: 22.02.2008
 * Time: 9:46:00
 */

trait ScTypeAlias extends ScPolymorphicElement with ScMember
  with ScDocCommentOwner with ScCommentOwner with ScDecoratedIconOwner {

  override protected def getBaseIcon(flags: Int): Icon =
    if (isDefinition) Icons.TYPE_ALIAS else Icons.ABSTRACT_TYPE_ALIAS

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

  def getTypeToken: PsiElement = findFirstChildByType(ScalaTokenTypes.kTYPE)

  def getOriginalElement: PsiElement = {
    val ccontainingClass = containingClass
    if (ccontainingClass == null) return this
    val originalClass: PsiClass = ccontainingClass.getOriginalElement.asInstanceOf[PsiClass]
    if (ccontainingClass eq  originalClass) return this
    if (!originalClass.isInstanceOf[ScTypeDefinition]) return this
    val c = originalClass.asInstanceOf[ScTypeDefinition]
    val aliasesIterator = c.aliases.iterator
    while (aliasesIterator.hasNext) {
      val alias = aliasesIterator.next()
      if (alias.name == name) return alias
    }
    this
  }

  def isDefinition: Boolean

  def physical: ScTypeAlias = this
}
