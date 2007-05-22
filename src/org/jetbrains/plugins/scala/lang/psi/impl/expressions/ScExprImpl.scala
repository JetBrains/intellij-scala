package org.jetbrains.plugins.scala.lang.psi.impl.expressions
/**
* @author Ilya Sergey
*/
import com.intellij.lang.ASTNode
import com.intellij.psi.impl.source.tree.TreeUtil
import com.intellij.psi.tree.IElementType
import com.intellij.psi.PsiElement

import org.jetbrains.plugins.scala.lang.psi._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.typechecker._

abstract class ScalaExpression( node : ASTNode ) extends ScalaPsiElementImpl(node) with IScalaExpression {
  import com.intellij.psi._

  def getAbstractType = (new ScalaTypeChecker).getTypeByTerm(this)
}

abstract class ScExpr1Impl(node : ASTNode) extends ScalaExpression(node) with ScResExpr

trait ScResExpr extends ScBlock

trait ScBlock extends PsiElement
