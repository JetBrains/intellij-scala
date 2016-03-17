package org.jetbrains.plugins.scala
package codeInspection.functionExpressions

import com.intellij.codeInspection._
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil
import com.intellij.psi.impl.source.tree.TreeElement
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiDocumentManager, PsiElement}
import org.jetbrains.plugins.scala.codeInspection.functionExpressions.MatchToPartialFunctionInspection._
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnTwoPsiElements, AbstractInspection}
import org.jetbrains.plugins.scala.extensions.childOf
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.result.Success
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScTypeExt}

import scala.collection.JavaConverters._

/**
 * Nikolay.Tropin
 * 9/26/13
 */
class MatchToPartialFunctionInspection extends AbstractInspection(inspectionId){
  def actionFor(holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case fun @ ScFunctionExpr(Seq(param), Some(ms @ ScMatchStmt(ref: ScReferenceExpression, _)))
      if ref.resolve() == param && !(param.typeElement.isDefined && notExpectedType(fun)) && checkSameResolve(fun) =>
      registerProblem(holder, ms, fun)
    case fun @ ScFunctionExpr(Seq(param), Some(ScBlock(ms @ ScMatchStmt(ref: ScReferenceExpression, _))))
      if ref.resolve() == param && !(param.typeElement.isDefined && notExpectedType(fun)) && checkSameResolve(fun) =>
      registerProblem(holder, ms, fun) //if fun is last statement in block, result can be block without braces
    case ms @ ScMatchStmt(und: ScUnderscoreSection, _) if checkSameResolve(ms) =>
      registerProblem(holder, ms, ms)
  }

  private def notExpectedType(expr: ScExpression) = {
    import expr.typeSystem
    (expr.getType(), expr.expectedType()) match {
      case (Success(tpe: ScType, _), Some(expType: ScType)) => !expType.equiv(tpe)
      case _ => true
    }
  }

  private def registerProblem(holder: ProblemsHolder, ms: ScMatchStmt, fExprToReplace: ScExpression) = {
    def leftBraceOffset(ms: ScMatchStmt): Option[Int] = {
      val leftBrace = ms.findFirstChildByType(ScalaTokenTypes.tLBRACE)
      leftBrace match {
        case elem: PsiElement => Option(elem.getTextRange.getStartOffset)
        case _ => None
      }
    }
    for (offset <- leftBraceOffset(ms)) {
      val endOffsetInParent = offset - fExprToReplace.getTextRange.getStartOffset
      val rangeInParent = new TextRange(0, endOffsetInParent)
      val fix = new MatchToPartialFunctionQuickFix(ms, fExprToReplace)
      holder.registerProblem(fExprToReplace, inspectionName, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, rangeInParent, fix)
    }
  }

  private def checkSameResolve(argExpr: ScExpression): Boolean = {
    def dummyCaseClauses = "{case _ => }"

    val call = PsiTreeUtil.getParentOfType(argExpr, classOf[MethodInvocation])
    val arg = argExpr match {
      case _ childOf (x childOf (_: ScArgumentExprList)) => x
      case _ childOf (x childOf (_: ScInfixExpr)) => x
      case _ => argExpr
    }
    if (call == null || !call.argumentExpressions.contains(arg)) return true
    val (refText, oldResolve) = call match {
      case ScInfixExpr(qual, r, _) => (s"${qual.getText}.${r.refName}", r.resolve())
      case ScMethodCall(r: ScReferenceExpression, _) => (r.getText, r.resolve())
      case _ => return true
    }

    val newCall = ScalaPsiElementFactory.createExpressionWithContextFromText(refText + dummyCaseClauses, call.getContext, call)
    newCall match {
      case ScMethodCall(ref: ScReferenceExpression, _) => ref.resolve() == oldResolve
      case _ => true
    }
  }
}

object MatchToPartialFunctionInspection {
  val inspectionId = "MatchToPartialFunction"
  val inspectionName = "Convert match statement to pattern matching anonymous function"
}

class MatchToPartialFunctionQuickFix(matchStmt: ScMatchStmt, fExprToReplace: ScExpression)
        extends AbstractFixOnTwoPsiElements(inspectionName, matchStmt, fExprToReplace) {
  def doApplyFix(project: Project) {
    val mStmt = getFirstElement
    val fExpr = getSecondElement
    val matchStmtCopy = mStmt.copy.asInstanceOf[ScMatchStmt]
    val leftBrace = matchStmtCopy.findFirstChildByType(ScalaTokenTypes.tLBRACE)
    if (leftBrace == null) return

    addNamingPatterns(matchStmtCopy, needNamingPattern(mStmt))
    matchStmtCopy.deleteChildRange(matchStmtCopy.getFirstChild, leftBrace.getPrevSibling)
    val newBlock = ScalaPsiElementFactory.createExpressionFromText(matchStmtCopy.getText, mStmt.getManager)
    CodeEditUtil.setOldIndentation(newBlock.getNode.asInstanceOf[TreeElement], CodeEditUtil.getOldIndentation(matchStmtCopy.getNode))
    extensions.inWriteAction {
      fExpr.getParent match {
        case (argList: ScArgumentExprList) childOf (call: ScMethodCall) if argList.exprs.size == 1 =>
          val newMethCall =
            ScalaPsiElementFactory.createExpressionFromText(call.getInvokedExpr.getText + " " + newBlock.getText, fExpr.getManager)
          call.replace(newMethCall)
        case block@ScBlock(`fExpr`) =>
          block.replace(newBlock)
        case _ =>
          fExpr.replace(newBlock)
      }
      PsiDocumentManager.getInstance(project).commitAllDocuments()
    }
  }

  private def needNamingPattern(matchStmt: ScMatchStmt): Seq[Int] = {
    matchStmt match {
      case ScMatchStmt(expr: ScReferenceExpression, _) =>
        val arg = expr.resolve()
        if (arg == null) return Nil
        val refs = ReferencesSearch.search(arg, new LocalSearchScope(matchStmt)).findAll().asScala
        for {
          (clause, index) <- matchStmt.caseClauses.zipWithIndex
          if refs.exists(ref => PsiTreeUtil.isAncestor(clause, ref.getElement, false))
        } yield index
      case _ => Nil
    }
  }

  private def addNamingPatterns(matchStmt: ScMatchStmt, indexes: Seq[Int]): Unit = {
    val clauses = matchStmt.caseClauses
    val name = matchStmt.expr.map(_.getText).getOrElse(return)
    indexes.map(i => clauses(i).pattern).foreach {
      case Some(w: ScWildcardPattern) => w.replace(ScalaPsiElementFactory.createPatternFromText(name, matchStmt.getManager))
      case Some(p: ScPattern) =>
        val newPatternText = if (needParentheses(p)) s"$name @ (${p.getText})" else s"$name @ ${p.getText}"
        p.replace(ScalaPsiElementFactory.createPatternFromText(newPatternText, matchStmt.getManager))
      case _ =>
    }
  }

  private def needParentheses(p: ScPattern): Boolean = p match {
    case _: ScReferencePattern | _: ScLiteralPattern | _: ScConstructorPattern |
         _: ScParenthesisedPattern | _: ScTuplePattern | _: ScStableReferenceElementPattern => false
    case _ => true
  }
}
