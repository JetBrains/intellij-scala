package org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef

import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameterClause
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateParents

trait ScGivenDefinition extends ScGivenInstance with ScTemplateDefinition {
  def hasCollectiveParam: Boolean
  def collectiveExtensionParamClause: Option[ScParameterClause]

  def templateParents: Option[ScTemplateParents]
}
