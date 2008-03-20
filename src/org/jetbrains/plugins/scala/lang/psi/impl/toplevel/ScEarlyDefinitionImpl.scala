package org.jetbrains.plugins.scala.lang.psi.impl.toplevel

import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.ASTNode

import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.lexer._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.annotations._
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes

import org.jetbrains.plugins.scala.icons.Icons

import org.jetbrains.plugins.scala.lang.psi.api.toplevel._

/** 
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 22.02.2008
* Time: 10:27:11
* To change this template use File | Settings | File Templates.
*/

class ScEarlyDefinitionImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScEarlyDefinition {
  override def toString: String = "EarlyDefinition"
}