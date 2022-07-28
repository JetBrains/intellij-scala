package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import com.intellij.psi._
import com.intellij.psi.impl.PsiImplUtil
import org.jetbrains.plugins.scala.extensions.{ASTNodeExt, ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ConstructorInvocationLike, ScConstructorInvocation}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.{createComma, createNewLineNode}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter

class ScArgumentExprListImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScArgumentExprList {
  override def toString: String = "ArgumentList"

  override def invocationCount: Int = {
    callExpression match {
      case call: ScMethodCall => call.args.invocationCount + 1
      case _ => 1
    }
  }

  override def callReference: Option[ScReferenceExpression] = {
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

  override def callGeneric: Option[ScGenericCall] = {
    getContext match {
      case call: ScMethodCall =>
        call.deepestInvokedExpr match {
          case gen: ScGenericCall => Some(gen)
          case _ => None
        }
      case _ => None
    }
  }

  override def callExpression: ScExpression = {
    getContext match {
      case call: ScMethodCall =>
        call.getEffectiveInvokedExpr
      case _ => null
    }
  }

  override def isUsing: Boolean =
    findChildByType(ScalaTokenType.UsingKeyword) != null

  override def matchedParameters: Seq[(ScExpression, Parameter)] = {
    getContext match {
      case call: ScMethodCall => call.matchedParameters
      case constrInvocation: ScConstructorInvocation =>
        constrInvocation.matchedParameters.filter {
          case (e, _) => this.isAncestorOf(e)
        }
      case _ => Seq.empty
    }
  }

  override def addBefore(element: PsiElement, anchor: PsiElement): PsiElement = {
    if (anchor == null) {
      if (exprs.isEmpty) {
        val par: PsiElement = findChildByType[PsiElement](ScalaTokenTypes.tLPARENTHESIS)
        if (par == null) return super.addBefore(element, anchor)
        super.addAfter(element, par)
      } else {
        val par: PsiElement = findChildByType[PsiElement](ScalaTokenTypes.tLPARENTHESIS)
        if (par == null) return super.addBefore(element, anchor)
        super.addAfter(par, createComma)
        super.addAfter(par, element)
      }
    } else {
      super.addBefore(element, anchor)
    }
  }

  override def addExpr(expr: ScExpression): ScArgumentExprList = {
    val par = findChildByType[PsiElement](ScalaTokenTypes.tLPARENTHESIS)
    val nextNode = par.getNode.getTreeNext
    val node = getNode
    val needCommaAndSpace = exprs.nonEmpty
    node.addChild(expr.getNode, nextNode)
    if (needCommaAndSpace) {
      node.addChild(comma, nextNode)
      node.addChild(space, nextNode)
    }
    this
  }

  override def addExprAfter(expr: ScExpression, anchor: PsiElement): ScArgumentExprList = {
    val nextNode = anchor.getNode.getTreeNext
    val node = getNode

    if (nextNode != null) {
      node.addChild(comma, nextNode)
      node.addChild(space, nextNode)
      node.addChild(expr.getNode, nextNode)
    } else {
      node.addChild(comma)
      node.addChild(space)
      node.addChild(expr.getNode)
    }
    this
  }
  private def comma = createComma.getNode

  private def space = createNewLineNode(" ")

  override def deleteChildInternal(child: ASTNode): Unit = {
    val exprs = this.exprs
    val childIsArgument = exprs.exists(_.getNode == child)
    def childIsLastArgumentToBeDeleted = exprs.lengthIs == 1 && childIsArgument
    def isLastArgumentClause = getParent match {
      case method@ScMethodCall(base, _) =>
        !base.is[ScMethodCall] && !method.getParent.is[ScMethodCall]
      case _: ConstructorInvocationLike =>
        !this.getPrevSiblingNotWhitespaceComment.is[ScArgumentExprList] &&
          !this.getNextSiblingNotWhitespaceComment.is[ScArgumentExprList]
      case _ => true
    }

    if (childIsLastArgumentToBeDeleted && !isLastArgumentClause) {
      this.delete()
    } else if (childIsArgument){
      if (childIsLastArgumentToBeDeleted) {
        val prev = PsiImplUtil.skipWhitespaceAndCommentsBack(child.getTreePrev)
        if (prev.hasElementType(ScalaTokenType.UsingKeyword)) {
          deleteChildInternal(prev)
        }
      }
      ScalaPsiUtil.deleteElementInCommaSeparatedList(this, child)
    } else {
      super.deleteChildInternal(child)
    }
  }
}
