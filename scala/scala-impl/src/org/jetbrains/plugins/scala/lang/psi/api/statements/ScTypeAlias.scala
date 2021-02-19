package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements

import com.intellij.psi.{PsiClass, PsiElement}
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScExistentialClause
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElementBase
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScDocCommentOwnerBase, ScMember, ScTypeDefinition}

/**
 * @author Alexander Podkhalyuzin
 * Date: 22.02.2008
 * Time: 9:46:00
 */
trait ScTypeAliasBase extends ScNamedElementBase with ScMember.WithBaseIconProvider with ScDocCommentOwnerBase with ScCommentOwnerBase { this: ScTypeAlias =>

  override protected def isSimilarMemberForNavigation(m: ScMember, isStrict: Boolean): Boolean = m match {
    case t: ScTypeAlias => t.name == name
    case _ => false
  }

  def isExistentialTypeAlias: Boolean = {
    getContext match {
      case _: ScExistentialClause => true
      case _ => false
    }
  }

  def getTypeToken: PsiElement = findFirstChildByType(ScalaTokenTypes.kTYPE).get

  override def getOriginalElement: PsiElement = {
    val ccontainingClass = containingClass
    if (ccontainingClass == null) return this
    val originalClass: PsiClass = ccontainingClass.getOriginalElement.asInstanceOf[PsiClass]
    if (ccontainingClass eq  originalClass) return this
    if (!originalClass.is[ScTypeDefinition]) return this
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