package org.jetbrains.plugins.scala.lang.psi.impl.base.patterns

import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import com.intellij.psi._

/** 
* @author Alexander Podkhalyuzin
* Date: 07.03.2008
*/

class ScParenthesisedPatternImpl(node: ASTNode) extends ScPatternImpl (node) with ScParenthesisedPattern {
  override def toString: String = "PatternInParenthesis"
}
