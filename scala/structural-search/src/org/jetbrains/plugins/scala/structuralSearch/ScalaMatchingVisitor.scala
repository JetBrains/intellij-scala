package org.jetbrains.plugins.scala.structuralSearch

import com.intellij.psi.PsiElement
import com.intellij.structuralsearch.impl.matcher.{CompiledPattern, GlobalMatchingVisitor}
import com.intellij.structuralsearch.impl.matcher.handlers.SubstitutionHandler
import org.jetbrains.plugins.scala.lang.psi.api.expr.{MethodInvocation, ScBlockExpr, ScIf, ScInfixExpr, ScMethodCall, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaElementVisitor, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral

class ScalaMatchingVisitor(globalVisitor: GlobalMatchingVisitor) extends ScalaElementVisitor {

  private def matchOptional(patO: Option[PsiElement], psiO: Option[PsiElement]): Boolean = {
    (patO, psiO) match {
      case (Some(pat), Some(psi)) => globalVisitor.`match`(pat, psi)
      case _ => false
    }
  }

  private def matchBody(patO: Option[PsiElement], psiO: Option[PsiElement]): Boolean = {
      (patO, psiO) match {
        case (Some(pat: ScBlockExpr), Some(psi: ScBlockExpr)) =>
          globalVisitor.matchSequentially(pat.getFirstChild, psi.getFirstChild)
        case (Some(pat: ScBlockExpr), Some(psi)) =>
          pat.statements.size == 1 && globalVisitor.`match`(pat.statements.head, psi)
        case (Some(pat), Some(psi: ScBlockExpr)) =>
          psi.statements.size == 1 && globalVisitor.`match`(pat, psi.statements.head)
        case (Some(pat), Some(psi)) =>
          globalVisitor.`match`(pat, psi)
        case _ => false
      }
  }


  override def visitIf(ifPat: ScIf): Unit = {
    val ifPsi = globalVisitor.getElement.asInstanceOf[ScIf]

    val condMatch = matchOptional(ifPat.condition, ifPsi.condition)
    val thenMatch = matchBody(ifPat.thenExpression, ifPsi.thenExpression)
    val elseMatch = (ifPat.elseExpression, ifPsi.elseExpression) match {
      case (None, None) => true
      case _ => matchBody(ifPat.elseExpression, ifPsi.elseExpression)
    }
    globalVisitor.setResult(condMatch && thenMatch && elseMatch)
  }

  override def visitInfixExpression(infixPat: ScInfixExpr): Unit = {
    visitMethodInvocation(infixPat)
//    val infixPsi = globalVisitor.getElement.asInstanceOf[ScInfixExpr]
//
//    val leftMatch = globalVisitor.`match`(infixPat.left, infixPsi.left)
//    val operationMatch = globalVisitor.`match`(infixPat.operation, infixPsi.operation)
//    val rightMatch = globalVisitor.`match`(infixPat.right, infixPsi.right)
//    globalVisitor.setResult(leftMatch && operationMatch && rightMatch)
  }

  override def visitLiteral(lPat: ScLiteral): Unit =
    globalVisitor.setResult(globalVisitor.matchText(lPat, globalVisitor.getElement))

  override def visitReferenceExpression(refPat: ScReferenceExpression): Unit = {
    val context = globalVisitor.getMatchContext
    val pattern = context.getPattern
    val other = globalVisitor.getElement
    val _handler = pattern.getHandlerSimple(refPat)
    _handler match {
      case substHand: SubstitutionHandler =>
        if (globalVisitor.setResult(substHand.validate(other, context)))
            substHand.addResult(other, context)
      case _ =>
        other match {
          case refExpr: ScReferenceExpression => globalVisitor.setResult(globalVisitor.`match`(refPat.getFirstChild, globalVisitor.getElement.getFirstChild))
          case _ => visitElement(refPat)
        }
    }
  }

  override def visitMethodCallExpression(call: ScMethodCall): Unit = visitMethodInvocation(call)

  def visitMethodInvocation(call: MethodInvocation): Unit = {
    val thisPat = call.thisExpr
    val invokedPat = call.getInvokedExpr.getLastChild
    val parsPat = call.argumentExpressions

    val other = globalVisitor.getElement.asInstanceOf[MethodInvocation]
    val thisPsi = other.thisExpr
    val invokedPsi = other.getInvokedExpr.getLastChild
    val parsPsi = other.argumentExpressions

    val thisMatch = globalVisitor.matchOptionally(thisPat.orNull, thisPsi.orNull)
    val methodMatch = globalVisitor.`match`(invokedPat, invokedPsi)
    val parsMatch = globalVisitor.matchSequentially(parsPat.toArray[PsiElement], parsPsi.toArray[PsiElement])
    globalVisitor.setResult(thisMatch && methodMatch && parsMatch)
  }

  override def visitScalaElement(element: ScalaPsiElement): Unit = visitElement(element)

  override def visitElement(elementPat: PsiElement): Unit = {
    val other = globalVisitor.getElement

    val handler = elementPat.getUserData(CompiledPattern.HANDLER_KEY)
    handler match {
      case substHandler: SubstitutionHandler =>
        globalVisitor.setResult(substHandler.handle(other, globalVisitor.getMatchContext))
      case null =>
        // todo do more useful stuff
        globalVisitor.setResult(globalVisitor.matchText(elementPat.getText, other.getText))
      case _ =>
        globalVisitor.setResult(handler.`match`(elementPat, other, globalVisitor.getMatchContext))
    }
  }
}