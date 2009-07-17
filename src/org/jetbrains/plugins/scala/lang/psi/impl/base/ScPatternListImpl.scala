package org.jetbrains.plugins.scala.lang.psi.impl.base

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.base._
import api.base.patterns._
/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScPatternListImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScPatternList{
  override def toString: String = "ListOfPatterns"

  def patterns: Seq[ScPattern] = findChildrenByClass(classOf[ScPattern])
}