package org.jetbrains.plugins.scala.lang.psi.impl.expr

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
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 07.03.2008
* Time: 10:04:45
* To change this template use File | Settings | File Templates.
*/

class ScArgumentExprListImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScArgumentExprList{
  override def toString: String = "ArgumentList"
}