package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements

import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotations
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockStatement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScDocCommentOwner, ScMember}
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable

/**
 * @author adkozlov
 */
trait ScValueOrVariable extends ScBlockStatement
  with ScMember.WithBaseIconProvider
  with ScDocCommentOwner
  with ScDeclaredElementsHolder
  with ScCommentOwner
  with Typeable {

  def keywordToken: PsiElement = findFirstChildByType(keywordElementType)

  protected def keywordElementType: IElementType

  def isAbstract: Boolean

  override def declaredElements: collection.Seq[ScTypedDefinition]

  def typeElement: Option[ScTypeElement]

  // makes sense for definitions only, not declarations, but convenient to have here not to complicate hierarchy
  def annotationAscription: Option[ScAnnotations] = None

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
