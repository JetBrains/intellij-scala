package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeArgs
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

/**
  * @author Alexander Podkhalyuzin
  */
trait ScInfixExpr extends ScExpression with ScSugarCallExpr {

  def lOp: ScExpression = findChildrenByClassScala(classOf[ScExpression]).apply(0)

  def operation: ScReferenceExpression = {
    val children = findChildrenByClassScala(classOf[ScExpression])
    if (children.length < 2) throw new RuntimeException("Wrong infix expression: " + getText)
    children.apply(1) match {
      case re: ScReferenceExpression => re
      case _ => throw new RuntimeException("Wrong infix expression: " + getText)
    }
  }

  def typeArgs: Option[ScTypeArgs] = {
    findChildrenByClassScala(classOf[ScTypeArgs]) match {
      case Array(tpArg: ScTypeArgs) => Some(tpArg)
      case _ => None
    }
  }

  def rOp: ScExpression = {
    val exprs: Array[ScExpression] = findChildrenByClassScala(classOf[ScExpression])
    assert(exprs.length > 2,
      s"Infix expression contains less than 3 expressions: ${exprs.mkString("(", ", ", ")")}, exprssion: $getText, full code: ${getContainingFile.getText}")
    exprs.apply(2)
  }

  def getBaseExpr: ScExpression = if (isRightAssoc) rOp else lOp

  def getArgExpr: ScExpression = if (isRightAssoc) lOp else rOp

  def isRightAssoc: Boolean = {
    val opText = ScalaNamesUtil.clean(operation.refName)
    opText.endsWith(":")
  }

  def isAssignmentOperator: Boolean = ParserUtils.isAssignmentOperator(operation.getText)

  def getInvokedExpr: ScExpression = operation

  def argsElement: PsiElement = getArgExpr

  override def accept(visitor: ScalaElementVisitor): Unit = {
    visitor.visitInfixExpression(this)
  }
}

object ScInfixExpr {
  def unapply(it: ScInfixExpr): Some[(ScExpression, ScReferenceExpression, ScExpression)] =
    Some(it.lOp, it.operation, it.rOp)
}