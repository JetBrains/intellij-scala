package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package patterns

import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils

/** 
* @author Alexander Podkhalyuzin
*/

trait ScParenthesisedPattern extends ScPattern with ScParenthesizedElement[ScPattern] {
  override def innerElement: Option[ScPattern] = findChild(classOf[ScPattern])

  override def isParenthesisClarifying: Boolean = {
    (getParent, innerElement) match {
      case (_: ScCompositePattern | _: ScNamingPattern | _: ScTuplePattern, _) => false
      case (p: ScPattern, Some(c)) if !isIndivisible(c) && getPrecedence(p) != getPrecedence(c) => true
      case _ => false
    }
  }

  override protected def getPrecedence(pattern: ScPattern): Int = pattern match {
    case _: ScCompositePattern => 12
    case _: ScNamingPattern => 11
    case ScInfixPattern(_, ifxOp, _) => 1 + ParserUtils.priority(ifxOp.getText) // varies from 1 to 10
    case _ => 0
  }
}

object ScParenthesisedPattern {
  def unapply(e: ScParenthesisedPattern): Option[ScPattern] = e.innerElement
}