package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition

/**
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

trait ScNewTemplateDefinition extends ScExpression with ScTemplateDefinition {
  override def getTextOffset: Int = extendsBlock.getTextOffset
}