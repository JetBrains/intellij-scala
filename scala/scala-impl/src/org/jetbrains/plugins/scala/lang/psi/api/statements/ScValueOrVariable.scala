package org.jetbrains.plugins.scala.lang.psi.api.statements

import com.intellij.psi.tree.IElementType
import com.intellij.psi.{PsiElement, PsiNamedElement}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotations
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockStatement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScDocCommentOwner, ScMember}
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable

trait ScValueOrVariable extends ScBlockStatement
  with ScMember
  with ScMember.WithBaseIconProvider
  with ScDocCommentOwner
  with ScDeclaredElementsHolder
  with ScCommentOwner
  with Typeable {

  def keywordToken: PsiElement = findFirstChildByType(keywordElementType).get

  protected def keywordElementType: IElementType

  def isAbstract: Boolean

  def isStable: Boolean

  override def declaredElements: Seq[ScTypedDefinition]

  def typeElement: Option[ScTypeElement]

  // makes sense for definitions only, not declarations, but convenient to have here not to complicate hierarchy
  def annotationAscription: Option[ScAnnotations] = None

  def declaredType: Option[ScType] =
    typeElement.flatMap {
      _.`type`().toOption
    }

  final def hasExplicitType: Boolean = typeElement.isDefined

  /**
   * Returns the offset in the file to which the caret should be placed
   * when performing the navigation to the element. (This trait does not implement
   * [[PsiNamedElement]] so return the offset of the keyword instead of the
   * name identifier)
   */
  override def getTextOffset: Int = keywordToken.getTextRange.getStartOffset

  override protected def isSimilarMemberForNavigation(member: ScMember, isStrictCheck: Boolean): Boolean = member match {
    case other: ScValueOrVariable =>
      for (thisName <- declaredNames;
           otherName <- other.declaredNames
           if thisName == otherName) {
        return true
      }
      super.isSimilarMemberForNavigation(member, isStrictCheck)
    case _ => false
  }
}
