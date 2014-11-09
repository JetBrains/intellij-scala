package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package templates

import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSelfTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScDeclaredElementsHolder, ScFunction, ScTypeAlias}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
* Time: 9:38:36
*/

trait ScTemplateBody extends ScalaPsiElement with ScControlFlowOwner {
  def members: Array[ScMember]

  def holders: Array[ScDeclaredElementsHolder]

  def functions: Array[ScFunction]

  def aliases: Array[ScTypeAlias]

  def typeDefinitions: Seq[ScTypeDefinition]

  def exprs: Array[ScExpression]

  def selfTypeElement: Option[ScSelfTypeElement]
}