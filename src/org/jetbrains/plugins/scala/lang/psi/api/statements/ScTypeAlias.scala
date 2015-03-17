package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements

import javax.swing.Icon

import com.intellij.psi.{PsiClass, PsiElement}
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScExistentialClause
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPolymorphicElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScDocCommentOwner, ScMember, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.light.scala.{ScLightTypeAliasDeclaration, ScLightTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.TypeAliasSignature

import scala.annotation.tailrec

/**
 * @author Alexander Podkhalyuzin
 * Date: 22.02.2008
 * Time: 9:46:00
 */

trait ScTypeAlias extends ScPolymorphicElement with ScMember with ScAnnotationsHolder with ScDocCommentOwner with ScCommentOwner {
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
}

object ScTypeAlias {
  @tailrec
  def getCompoundCopy(sign: TypeAliasSignature, ta: ScTypeAlias): ScTypeAlias = {
    ta match {
      case light: ScLightTypeAliasDeclaration => getCompoundCopy(sign, light.ta)
      case light: ScLightTypeAliasDefinition  => getCompoundCopy(sign, light.ta)
      case decl: ScTypeAliasDeclaration       => new ScLightTypeAliasDeclaration(sign, decl)
      case definition: ScTypeAliasDefinition  => new ScLightTypeAliasDefinition(sign, definition)
    }
  }
}