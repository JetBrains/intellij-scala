package org.jetbrains.plugins.scala.lang.psi.api.statements

import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockStatement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScDocCommentOwner, ScMember}
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable

/**
  * @author adkozlov
  */
trait ScValueOrVariable extends ScBlockStatement with ScMember with ScDocCommentOwner with ScDeclaredElementsHolder
  with ScAnnotationsHolder with ScCommentOwner with Typeable {
  def keywordToken: PsiElement = findFirstChildByType(keywordElementType)

  protected def keywordElementType: IElementType

  def declaredElements: Seq[ScTypedDefinition]

  def typeElement: Option[ScTypeElement]

  def declaredType: Option[ScType] =
    typeElement.flatMap {
      _.`type`().toOption
    }

  final def hasExplicitType: Boolean = typeElement.isDefined

  override protected def isSimilarMemberForNavigation(member: ScMember, isStrict: Boolean): Boolean = member match {
    case other: ScValueOrVariable =>
      for (thisName <- declaredNames;
           otherName <- other.declaredNames
           if thisName == otherName) {
        return true
      }
      super.isSimilarMemberForNavigation(member, isStrict)
    case _ => false
  }
}
