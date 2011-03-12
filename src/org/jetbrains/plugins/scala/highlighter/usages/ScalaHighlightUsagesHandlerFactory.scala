package org.jetbrains.plugins.scala.highlighter.usages

import com.intellij.codeInsight.highlighting.{HighlightUsagesHandlerBase, HighlightUsagesHandlerFactory}
import com.intellij.openapi.editor.Editor
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScVariableDefinition, ScPatternDefinition, ScFunction, ScFunctionDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.expr._

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.12.2009
 */

class ScalaHighlightUsagesHandlerFactory extends  HighlightUsagesHandlerFactory {
  def createHighlightUsagesHandler(editor: Editor, file: PsiFile): HighlightUsagesHandlerBase[_ <: PsiElement] = {
    if (!file.isInstanceOf[ScalaFile]) return null
    val element: PsiElement = file.findElementAt(editor.getCaretModel.getOffset)
    if (element == null || element.getNode == null) return null
    element.getNode.getElementType match {
      case ScalaTokenTypes.kRETURN => {
        val fun = PsiTreeUtil.getParentOfType(element, classOf[ScFunctionDefinition])
        if (fun != null) return new ScalaHighlightExitPointsHandler(fun, editor, file)
      }
      case ScalaTokenTypes.kDEF => {
        val fun = PsiTreeUtil.getParentOfType(element, classOf[ScFunction])
        fun match {
          case d: ScFunctionDefinition => return new ScalaHighlightExitPointsHandler(d, editor, file)
          case _ =>
        }
      }
      case ScalaTokenTypes.kVAL => {
        val patternNullable = PsiTreeUtil.getParentOfType(element, classOf[ScPatternDefinition])
        Option(patternNullable) match {
          case Some(pattern) if pattern.pList.allPatternsSimple && pattern.pList.patterns.length == 1 =>
            return new ScalaHighlightExprResultHandler(pattern.expr, editor, file)
          case _ =>
        }
      }
      case ScalaTokenTypes.kVAR => {
        val patternNullable = PsiTreeUtil.getParentOfType(element, classOf[ScVariableDefinition])
        Option(patternNullable) match {
          case Some(pattern) if pattern.pList.allPatternsSimple && pattern.pList.patterns.length == 1 =>
            return new ScalaHighlightExprResultHandler(pattern.expr, editor, file)
          case _ =>
        }
      }
      case ScalaTokenTypes.kCASE => {
        val caseClauseNullable = PsiTreeUtil.getParentOfType(element, classOf[ScCaseClause])
        for {
          cc <- Option(caseClauseNullable)
          expr <- cc.expr
        } {
          return new ScalaHighlightExprResultHandler(expr, editor, file)
        }
      }
      case ScalaTokenTypes.kMATCH => {
        val matchStmtNullable = PsiTreeUtil.getParentOfType(element, classOf[ScMatchStmt])
        Option(matchStmtNullable) match {
          case Some(matchStmt) =>
            return new ScalaHighlightExprResultHandler(matchStmt, editor, file)
          case _ =>
        }
      }
      case ScalaTokenTypes.kTRY => {
        val tryStmtNullable = PsiTreeUtil.getParentOfType(element, classOf[ScTryStmt])
        Option(tryStmtNullable) match {
          case Some(tryStmt) =>
            return new ScalaHighlightExprResultHandler(tryStmt, editor, file)
          case _ =>
        }
      }
      case ScalaTokenTypes.kFOR => {
        val forStmtNullable = PsiTreeUtil.getParentOfType(element, classOf[ScForStatement])
        for {
          forStmt <- Option(forStmtNullable)
          if forStmt.isYield
          body <- forStmt.body
        } {
          return new ScalaHighlightExprResultHandler(body, editor, file)
        }
      }
      case ScalaTokenTypes.kIF => {
        val ifStmtNullable = PsiTreeUtil.getParentOfType(element, classOf[ScIfStmt])
        for {
          ifStmt <- Option(ifStmtNullable)
        } {
          return new ScalaHighlightExprResultHandler(ifStmt, editor, file)
        }
      }
      case _ =>
    }
    return null
  }
}