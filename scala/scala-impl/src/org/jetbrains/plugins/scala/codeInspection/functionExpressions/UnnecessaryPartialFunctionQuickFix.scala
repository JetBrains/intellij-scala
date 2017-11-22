package org.jetbrains.plugins.scala.codeInspection.functionExpressions

import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiWhiteSpace}
import org.jetbrains.plugins.scala.codeInspection.functionExpressions.UnnecessaryPartialFunctionQuickFix._
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, InspectionBundle}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCaseClause, ScReferencePattern, ScWildcardPattern}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlock, ScBlockExpr}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText

object UnnecessaryPartialFunctionQuickFix {
  val hint = InspectionBundle.message("convert.to.anonymous.function")
}

class UnnecessaryPartialFunctionQuickFix(expression: ScBlockExpr)
  extends AbstractFixOnPsiElement(hint, expression) {

  override protected def doApplyFix(expr: ScBlockExpr)
                                   (implicit project: Project): Unit = {
    val expressionCopy = expr.copy().asInstanceOf[ScBlockExpr]
    expressionCopy.caseClauses.map(_.caseClauses).foreach {
      case Seq(singleCaseClause) =>
        removeCaseKeyword(singleCaseClause)
        if(canConvertBraces(singleCaseClause)){
          ScalaPsiUtil.replaceBracesWithParentheses(expressionCopy)
          deleteLeadingWhitespace(expressionCopy)
          deleteTrailingWhitespace(expressionCopy)
        }
        expr.replace(createExpressionFromText(expressionCopy.getText))
      case _ => 
    }
  }

  def deleteLeadingWhitespace(expressionCopy: ScBlockExpr): Unit =
    Option(expressionCopy.findFirstChildByType(ScalaTokenTypes.tLPARENTHESIS))
      .map(_.getNextSibling)
      .foreach(deleteIfWhitespace)

  def deleteTrailingWhitespace(expressionCopy: ScBlockExpr): Unit =
    Option(expressionCopy.findFirstChildByType(ScalaTokenTypes.tRPARENTHESIS))
      .map(_.getPrevSibling)
      .foreach(deleteIfWhitespace)

  def deleteIfWhitespace(element: PsiElement): Unit =
    if(element.isInstanceOf[PsiWhiteSpace]) element.delete()

  private def removeCaseKeyword(clause: ScCaseClause) =
    for {
      caseKeyword <- clause.firstChild
      whitespaceBeforePattern <- clause.pattern.map(_.getPrevSibling)
    } clause.deleteChildRange(caseKeyword, whitespaceBeforePattern)

  private def canConvertBraces(clause: ScCaseClause): Boolean =
    patternIsReferenceOrWildcard(clause) && bodyIsOneLineExpression(clause)

  private def patternIsReferenceOrWildcard(clause: ScCaseClause): Boolean =
    clause.pattern.exists {
      case _: ScReferencePattern => true
      case _: ScWildcardPattern => true
      case _ => false
    }

  private def bodyIsOneLineExpression(clause: ScCaseClause): Boolean = {
    clause.expr.exists {
      case block: ScBlock => block.statements.size == 1 && block.getText.lines.size == 1
      case _ => false
    }
  }
}
