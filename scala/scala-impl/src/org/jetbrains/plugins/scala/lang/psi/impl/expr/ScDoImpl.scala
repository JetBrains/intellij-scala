package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr._

class ScDoImpl(node: ASTNode) extends ScExpressionImplBase(node) with ScDo {

  override def body: Option[ScExpression] = findChild[ScExpression]

  override def condition: Option[ScExpression] = {
    val rpar = findChildByType[PsiElement](ScalaTokenTypes.tLPARENTHESIS)
    val c = if (rpar != null) PsiTreeUtil.getNextSiblingOfType(rpar, classOf[ScExpression]) else null
    Option(c)
  }

  override def toString: String = "DoStatement"
}