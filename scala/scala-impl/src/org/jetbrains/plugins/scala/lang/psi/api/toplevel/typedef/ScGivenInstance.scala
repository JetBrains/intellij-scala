package org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef

import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameterClause, ScParameters}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScCommentOwner, ScDeclaredElementsHolder}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateParents
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScGivenInstance._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScTypeParametersOwner, ScTypedDefinition}

// todo: maybe move given stuff to another package
trait ScGivenInstance extends ScMember.WithBaseIconProvider
  with ScTypeParametersOwner
  with ScTypedDefinition
  with ScDeclaredElementsHolder
  with ScDocCommentOwner
  with ScCommentOwner  {

  def isAnonymous: Boolean
  def flavor: Flavor

  def givenParameterClausesElement: Option[ScParameters]
  def givenParameterClauses: Seq[ScParameterClause]
}

object ScGivenInstance {
  sealed abstract class Flavor

  object GivenAlias extends Flavor {
    def unapply(alias: ScGivenAlias): Option[ScGivenAlias] = Some(alias)
  }

  object CollectiveExtMethod extends Flavor {
    def unapply(givenDef: ScGivenDefinition): Option[(ScGivenDefinition, ScParameterClause)] =
      givenDef.collectiveExtensionParamClause.map(givenDef -> _)
  }

  object GivenImplementation extends Flavor {
    def unapply(givenDef: ScGivenDefinition): Option[(ScGivenDefinition, Option[ScTemplateParents])] =
      if (givenDef.hasCollectiveParam) None
      else Some(givenDef -> givenDef.templateParents)
  }
}