package org.jetbrains.plugins.scala.lang.psi.api.toplevel
package typedef

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScCommentOwner, ScDeclaredElementsHolder, ScParameterOwner}

trait ScGiven extends ScalaPsiElement
  with ScNamedElement
  with ScTypedDefinition // TODO It's a subtype of ScNamedElement
  with ScMember
  with ScMember.WithBaseIconProvider
  with ScCommentOwner
  with ScDocCommentOwner
  with ScParameterOwner.WithContextBounds
  with ScDeclaredElementsHolder {

  // TODO Why it's a subtype of ScNamedElement if there might be no name?
  def nameElement: Option[PsiElement]

  override def getNavigationElement: PsiElement =
    if (nameElement.isDefined) super.getNavigationElement else getFirstChild
}

object ScGiven {
  def generateAnonymousGivenName(ty: ScTypeElement): String = {
    // todo: implement correct naming : https://dotty.epfl.ch/docs/reference/contextual/relationship-implicits.html#anonymous-given-instances
    var text = ty.getText
    text = text.replaceAll("=>", "_to_")
    text = text.replaceAll("[^a-zA-Z_0-9]+", "_")
    text = text.replaceAll("(^_+)|(_+$)", "")

    "given_" + text
  }
}