package org.jetbrains.plugins.scala.lang.psi.impl.base

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.impl.patterns._
import org.jetbrains.plugins.scala.lang.psi.impl.expressions._
import org.jetbrains.plugins.scala.lang.psi.impl.types._
import org.jetbrains.plugins.scala.lang.psi.impl.top.params._

import com.intellij.psi.tree.TokenSet
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType;
import com.intellij.psi._

import org.jetbrains.annotations._
import org.jetbrains.plugins.scala.lang.formatting.patterns.indent._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.icons.Icons

import org.jetbrains.plugins.scala.lang.psi.api.base._

/** 
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 07.03.2008
* Time: 12:15:06
* To change this template use File | Settings | File Templates.
*/

class ScPrimaryConstructorImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScPrimaryConstructor{
  override def toString: String = "PrimaryConstructor"
}