package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package typedef

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScCommentOwner, ScDeclaredElementsHolder, ScParameterOwner}

trait ScGiven extends ScalaPsiElement
  with ScNamedElement
  with ScTypedDefinition
  with ScMember.WithBaseIconProvider
  with ScCommentOwner
  with ScDocCommentOwner
  with ScParameterOwner.WithContextBounds
  with ScDeclaredElementsHolder {

  def nameElement: Option[PsiElement]
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