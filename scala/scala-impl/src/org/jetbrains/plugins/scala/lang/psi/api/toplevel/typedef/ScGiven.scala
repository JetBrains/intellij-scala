package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package typedef

import org.jetbrains.plugins.scala.lang.psi.api._
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameters, ScTypeParamClause}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScCommentOwner, ScCommentOwnerBase, ScDeclaredElementsHolder}

trait ScGivenBase extends ScalaPsiElementBase with ScNamedElementBase with ScTypedDefinitionBase with ScMember.WithBaseIconProvider with ScCommentOwnerBase with ScDocCommentOwnerBase with ScDeclaredElementsHolder { this: ScGiven =>
  // Given signature elements
  def givenName: Option[PsiElement]
  def givenTypeParamClause: Option[ScTypeParamClause]
  def givenParameters: Option[ScParameters]
}

abstract class ScGivenCompanion {
  def generateAnonymousGivenName(ty: ScTypeElement): String = {
    // todo: implement correct naming : https://dotty.epfl.ch/docs/reference/contextual/relationship-implicits.html#anonymous-given-instances
    var text = ty.getText
    text = text.replaceAll("=>", "_to_")
    text = text.replaceAll("[^a-zA-Z_0-9]+", "_")
    text = text.replaceAll("(^_+)|(_+$)", "")

    "given_" + text
  }
}