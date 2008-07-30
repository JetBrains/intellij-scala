package org.jetbrains.plugins.scala.lang.psi.impl.base.patterns

import api.base.patterns._
import psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import com.intellij.psi._
import lang.lexer._
import psi.types.Nothing

/** 
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

class ScReferencePatternImpl(node: ASTNode) extends ScBindingPatternImpl (node) with ScReferencePattern{
  override def toString: String = "ReferencePattern"
  override def calcType = expectedType match {
    case Some(t) => t
    case None => Nothing
  }
}