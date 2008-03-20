package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef

import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.ASTNode

import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.lexer._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.annotations._
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.formatting.patterns.indent._
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._

/** 
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 20.02.2008
* Time: 18:48:06
* To change this template use File | Settings | File Templates.
*/

class ScObjectImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScObject{

  override def toString: String = "ScObject"
  override def getIcon(flags: Int) = Icons.OBJECT
  def setName(s: String) = this
}