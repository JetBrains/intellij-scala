package org.jetbrains.plugins.scala.lang.psi.impl.base.patterns

import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode

/** 
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

class ScReferencePatternImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScReferencePattern{
  override def toString: String = "ReferencePattern"
}