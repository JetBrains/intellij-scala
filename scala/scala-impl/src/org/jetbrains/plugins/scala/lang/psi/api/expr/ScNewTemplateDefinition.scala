package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import org.jetbrains.plugins.scala.lang.psi.api.base.ScConstructor
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition

/**
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/
trait ScNewTemplateDefinition extends ScExpression with ScTemplateDefinition {
  def constructor: Option[ScConstructor]

  //It's very rare case, when we need to desugar apply first.
  def desugaredApply: Option[ScExpression]

  override def getTextOffset: Int = extendsBlock.getTextOffset
}