package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import api.expr._
import types.Nothing
import com.intellij.psi.PsiElement
import lexer.ScalaTokenTypes

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

class ScReturnStmtImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScReturnStmt {
  override def toString: String = "ReturnStatement"

  override def getType = Nothing

  def returnKeyword: PsiElement = findChildByType(ScalaTokenTypes.kRETURN)
}