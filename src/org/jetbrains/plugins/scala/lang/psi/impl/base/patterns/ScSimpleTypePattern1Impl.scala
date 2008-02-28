package org.jetbrains.plugins.scala.lang.psi.impl.base.patterns

import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode

/** 
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 28.02.2008
* Time: 16:14:43
* To change this template use File | Settings | File Templates.
*/

class ScSimpleTypePattern1Impl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScSimpleTypePattern1{
  override def toString: String = "ElementaryTypePattern"
}