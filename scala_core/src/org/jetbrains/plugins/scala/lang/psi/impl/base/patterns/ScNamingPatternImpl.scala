package org.jetbrains.plugins.scala.lang.psi.impl.base.patterns

import api.base.patterns._
import psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import com.intellij.psi._

/**
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

class ScNamingPatternImpl(node: ASTNode) extends ScBindingPatternImpl (node) with ScNamingPattern{
  override def toString: String = "NamingPattern"

  override def calcType = if (named == null) psi.types.Nothing else named.calcType //todo fix parser
}