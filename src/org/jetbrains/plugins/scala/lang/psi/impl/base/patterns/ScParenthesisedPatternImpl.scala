package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package patterns

import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import com.intellij.psi._

/** 
* @author Alexander Podkhalyuzin
* Date: 07.03.2008
*/

class ScParenthesisedPatternImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScParenthesisedPattern {
  override def toString: String = "PatternInParenthesis"
}
