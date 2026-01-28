package org.jetbrains.plugins.scala.codeInspection.functionExpressions

import com.intellij.modcommand.{ActionContext, ModPsiUpdater, PsiUpdateModCommandAction}
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiDocumentManager, PsiElement, PsiWhiteSpace}
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.codeInspection.functionExpressions.UnnecessaryPartialFunctionQuickFix._
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCaseClause, ScReferencePattern, ScWildcardPattern}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlock, ScBlockExpr}

object UnnecessaryPartialFunctionQuickFix {
  val hint: String = ScalaInspectionBundle.message("convert.to.anonymous.function")
}

final class UnnecessaryPartialFunctionQuickFix(expression: ScBlockExpr) extends PsiUpdateModCommandAction[ScBlockExpr](expression) {
  override def getFamilyName: String = hint

  override protected def invoke(context: ActionContext, expr: ScBlockExpr, updater: ModPsiUpdater): Unit = {
    implicit val project: Project = context.project()
    var expressionCopy = expr.copy().asInstanceOf[ScBlockExpr]
    expressionCopy.caseClauses.map(_.caseClauses).foreach {
      case Seq(singleCaseClause) =>
        removeCaseKeyword(singleCaseClause)
        if (canConvertBraces(singleCaseClause)) {
          expressionCopy = ScalaPsiUtil.convertBlockToBraced(expressionCopy)
          ScalaPsiUtil.replaceBracesWithParentheses(expressionCopy)
          deleteLeadingWhitespace(expressionCopy)
          deleteTrailingWhitespace(expressionCopy)
        }
        val prevWs = expr.prevLeaf.filter(_.isWhitespace)
        val exprRange = expr.getTextRange
        val documentManager = PsiDocumentManager.getInstance(project)
        val document = updater.getDocument
        document.replaceString(exprRange.getStartOffset, exprRange.getEndOffset, expressionCopy.getText)
        documentManager.commitDocument(document)
        prevWs.foreach(_.delete())
        documentManager.doPostponedOperationsAndUnblockDocument(document)
      case _ =>
    }
  }

  private def deleteLeadingWhitespace(expressionCopy: ScBlockExpr): Unit =
    expressionCopy.findFirstChildByType(ScalaTokenTypes.tLPARENTHESIS)
      .map(_.getNextSibling)
      .foreach(deleteIfWhitespace)

  private def deleteTrailingWhitespace(expressionCopy: ScBlockExpr): Unit =
    expressionCopy.findFirstChildByType(ScalaTokenTypes.tRPARENTHESIS)
      .map(_.getPrevSibling)
      .foreach(deleteIfWhitespace)

  private def deleteIfWhitespace(element: PsiElement): Unit =
    if (element.is[PsiWhiteSpace]) element.delete()

  private def removeCaseKeyword(clause: ScCaseClause): Unit =
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
      case block: ScBlock => block.statements.size == 1 && block.getText.linesIterator.size == 1
      case _ => false
    }
  }
}
