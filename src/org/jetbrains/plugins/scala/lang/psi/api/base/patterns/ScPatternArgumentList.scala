package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package patterns

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSequenceArg
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._

/**
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

trait ScPatternArgumentList extends ScArguments {

  def patterns: Seq[ScPattern]

  def missedLastExpr: Boolean = {
    var child = getLastChild
    while (child != null && child.getNode.getElementType != ScalaTokenTypes.tCOMMA) {
      if (child.isInstanceOf[ScPattern] || child.isInstanceOf[ScSequenceArg]) return false
      child = child.getPrevSibling
    }
    return child != null && child.getNode.getElementType == ScalaTokenTypes.tCOMMA
  }
}