package org.jetbrains.plugins.scala.lang.psi.impl.base.patterns

import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import com.intellij.lang.ASTNode
import com.intellij.psi._

/** 
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

class ScWildcardPatternImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScWildcardPattern{
  override def toString: String = "WildcardPattern"
}