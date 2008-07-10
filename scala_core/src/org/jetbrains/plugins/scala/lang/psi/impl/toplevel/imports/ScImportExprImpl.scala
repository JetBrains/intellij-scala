package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.imports

import com.intellij.util.IncorrectOperationException
import api.base.ScStableCodeReferenceElement
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

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports._

/** 
* @author Alexander Podkhalyuzin
* Date: 20.02.2008
*/

class ScImportExprImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScImportExpr {
  override def toString: String = "ImportExpression"

  def singleWildcard = findChildByType(ScalaTokenTypes.tUNDER) != null

  def qualifier: ScStableCodeReferenceElement = if (!singleWildcard &&
          (selectorSet match {case None => true case _ => false})) reference match {
    case Some(x) => x.qualifier match {case None => throw new IncorrectOperationException case Some(x) => x}
    case _ => throw new IncorrectOperationException
  } else reference match {
    case Some(x) => x
    case _ => throw new IncorrectOperationException
  }

  def deleteExpr {
    val parent = getParent.asInstanceOf[ScImportStmt]
    if (parent.importExprs.size == 1) parent.deleteStmt
    else parent.getNode.removeChild(getNode)
  }
}