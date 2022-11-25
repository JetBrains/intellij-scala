package org.jetbrains.plugins.scala.lang.psi.api.toplevel
package templates

import org.jetbrains.plugins.scala.lang.psi.api.base.ScOptionalBracesOwner
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSelfTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.{ScControlFlowOwner, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.psi.{ScExportsHolder, ScImportsHolder}

trait ScTemplateBody extends ScalaPsiElement
  with ScControlFlowOwner
  with ScImportsHolder
  with ScExportsHolder
  with ScOptionalBracesOwner {

  def members: Seq[ScMember]

  def holders: Seq[ScDeclaredElementsHolder]

  def functions: Seq[ScFunction]

  def properties: Seq[ScValueOrVariable]

  def aliases: Seq[ScTypeAlias]

  def cases: Seq[ScEnumCases]

  def typeDefinitions: Seq[ScTypeDefinition]

  def exprs: Seq[ScExpression]

  def selfTypeElement: Option[ScSelfTypeElement]

  def extensions: Seq[ScExtension]
}
