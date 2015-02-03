package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter

/**
* @author Alexander Podkhalyuzin
* Date: 07.03.2008
*/

class ScArgumentExprListImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScArgumentExprList {
  override def toString: String = "ArgumentList"

  def invocationCount: Int = {
    callExpression match {
      case call: ScMethodCall => call.args.invocationCount + 1
      case _ => 1
    }
  }

  def callReference: Option[ScReferenceExpression] = {
    getContext match {
      case call: ScMethodCall =>
        call.deepestInvokedExpr match {
          case ref: ScReferenceExpression => Some(ref)
          case gen: ScGenericCall =>
            gen.referencedExpr match {
              case ref: ScReferenceExpression => Some(ref)
              case _ => None
            }
          case _ => None
        }
      case _ => None
    }
  }

  def callGeneric: Option[ScGenericCall] = {
    getContext match {
      case call: ScMethodCall =>
        call.deepestInvokedExpr match {
          case gen: ScGenericCall => Some(gen)
          case _ => None
        }
      case _ => None
    }
  }

  def callExpression: ScExpression = {
    getContext match {
      case call: ScMethodCall =>
        call.getEffectiveInvokedExpr
      case _ => null
    }
  }

  def matchedParameters: Option[Seq[(ScExpression, Parameter)]] = {
    getContext match {
      case call: ScMethodCall =>
        Some(call.matchedParameters)
      case _ => None
    }
  }

  override def addBefore(element: PsiElement, anchor: PsiElement): PsiElement = {
    if (anchor == null) {
      if (exprs.length == 0) {
        val par: PsiElement = findChildByType(ScalaTokenTypes.tLPARENTHESIS)
        if (par == null) return super.addBefore(element, anchor)
        super.addAfter(element, par)
      } else {
        val par: PsiElement = findChildByType(ScalaTokenTypes.tLPARENTHESIS)
        if (par == null) return super.addBefore(element, anchor)
        val comma = ScalaPsiElementFactory.createComma(getManager)
        super.addAfter(par, comma)
        super.addAfter(par, element)
      }
    } else {
      super.addBefore(element, anchor)
    }
  }

  def addExpr(expr: ScExpression): ScArgumentExprList = {
    val par = findChildByType[PsiElement](ScalaTokenTypes.tLPARENTHESIS)
    val nextNode = par.getNode.getTreeNext
    val node = getNode
    val needCommaAndSpace = exprs.nonEmpty
    node.addChild(expr.getNode, nextNode)
    if (needCommaAndSpace) {
      val comma = ScalaPsiElementFactory.createComma(getManager)
      val space = ScalaPsiElementFactory.createNewLineNode(getManager, " ")
      node.addChild(comma.getNode, nextNode)
      node.addChild(space, nextNode)
    }
    this
  }

  def addExprAfter(expr: ScExpression, anchor: PsiElement): ScArgumentExprList = {
    val nextNode = anchor.getNode.getTreeNext
    val comma = ScalaPsiElementFactory.createComma(getManager)
    val space = ScalaPsiElementFactory.createNewLineNode(getManager, " ")
    val node = getNode
    if (nextNode != null) {
      node.addChild(comma.getNode, nextNode)
      node.addChild(space, nextNode)
      node.addChild(expr.getNode, nextNode)
    } else {
      node.addChild(comma.getNode)
      node.addChild(space)
      node.addChild(expr.getNode)
    }
    this
  }
}