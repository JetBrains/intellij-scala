package org.jetbrains.plugins.scala.lang.psi.impl.base.patterns

import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import com.intellij.psi._

/** 
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

class ScConstructorPatternImpl(node: ASTNode) extends ScPatternImpl (node) with ScConstructorPattern {

  override def toString: String = "ConstructorPattern"

  def args = findChildByClass(classOf[ScPatternArgumentList])

  override def subpatterns : Seq[ScPattern]= if (args != null) args.patterns else Seq.empty
}