package org.jetbrains.plugins.scala.lang.psi.impl.base.patterns

import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import com.intellij.psi._

/**
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

class ScNamingPatternImpl(node: ASTNode) extends ScBindingPatternImpl (node) with ScNamingPattern{

  override def toString: String = "NamingPattern"

  //todo implement me!
  def isSeqBindingPattern: Boolean = false
}