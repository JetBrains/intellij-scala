package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import org.jetbrains.plugins.scala.lang.psi.api._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScConstructorInvocation
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTemplateDefinition, ScTemplateDefinitionBase}

/**
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/
trait ScNewTemplateDefinitionBase extends ScExpressionBase with ScTemplateDefinitionBase { this: ScNewTemplateDefinition =>
  def constructorInvocation: Option[ScConstructorInvocation]

  //It's very rare case, when we need to desugar apply first.
  def desugaredApply: Option[ScExpression]

  override def getTextOffset: Int = extendsBlock.getTextOffset
}