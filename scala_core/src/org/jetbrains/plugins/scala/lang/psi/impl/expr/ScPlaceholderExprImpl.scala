package org.jetbrains.plugins.scala.lang.psi.impl.expr

import _root_.org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.psi.tree.TokenSet
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType;
import com.intellij.psi._

import org.jetbrains.annotations._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.icons.Icons


import org.jetbrains.plugins.scala.lang.psi.api.expr._

/**
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

class ScPlaceholderExprImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScPlaceholderExpr {
  override def toString: String = "PlaceholderExpression"


  override def getType(): ScType = {
   placeholdedExpr match {
     case Some(x) => x.getType
     case None => types.Nothing //todo: according to exprected type
   }
  }
}