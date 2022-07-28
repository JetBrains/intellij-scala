package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import org.jetbrains.plugins.scala.lang.psi.api.base.ScConstructorInvocation
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition

trait ScNewTemplateDefinition extends ScExpression with ScTemplateDefinition {
  def firstConstructorInvocation: Option[ScConstructorInvocation]

  //It's very rare case, when we need to desugar apply first.
  def desugaredApply: Option[ScExpression]

  override def getTextOffset: Int = extendsBlock.getTextOffset
}