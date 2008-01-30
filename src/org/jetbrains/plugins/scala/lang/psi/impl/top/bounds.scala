package org.jetbrains.plugins.scala.lang.psi.impl.top

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi._
import com.intellij.psi.tree._

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi._
import org.jetbrains.plugins.scala.lang.formatting.patterns.indent._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.containers._
import org.jetbrains.plugins.scala.lang.psi.impl.top.templateStatements._

/**
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 30.01.2008
* Time: 17:22:49
* To change this template use File | Settings | File Templates.
*/

case class ScLowerBoundImpl(node: ASTNode) extends ScalaPsiElementImpl(node) {
  override def toString: String = "Lower bound type"
}

case class ScUpperBoundImpl(node: ASTNode) extends ScalaPsiElementImpl(node) {
  override def toString: String = "Upper bound type"
}