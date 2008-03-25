package org.jetbrains.plugins.scala.lang.psi.impl.base.patterns

import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode

/** 
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 28.02.2008
* Time: 16:13:00
* To change this template use File | Settings | File Templates.
*/

class ScWildPatternImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScWildPattern{
  override def toString: String = "WildPattern"
}