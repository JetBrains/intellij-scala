package org.jetbrains.plugins.scala.highlighter.usages

import com.intellij.codeInsight.TargetElementUtilBase
import com.intellij.codeInsight.highlighting.{HighlightUsagesHandlerBase, HighlightUsagesHandlerFactory}
import com.intellij.openapi.editor.Editor
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDefinition, ScPatternDefinition, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.12.2009
 */

class ScalaHighlightUsagesHandlerFactory extends  HighlightUsagesHandlerFactory {
  def createHighlightUsagesHandler(editor: Editor, file: PsiFile): HighlightUsagesHandlerBase[_ <: PsiElement] = {
    if (!file.isInstanceOf[ScalaFile]) return null
    val offset = TargetElementUtilBase.adjustOffset(file, editor.getDocument, editor.getCaretModel.getOffset)
    val element: PsiElement = file.findElementAt(offset)
    if (element == null || element.getNode == null) return null
    element.getNode.getElementType match {
      case ScalaTokenTypes.kRETURN => {
        val fun = PsiTreeUtil.getParentOfType(element, classOf[ScFunctionDefinition])
        if (fun != null) return new ScalaHighlightExitPointsHandler(fun, editor, file, element)
      }
      case ScalaTokenTypes.kDEF => {
        val fun = PsiTreeUtil.getParentOfType(element, classOf[ScFunction])
        fun match {
          case d: ScFunctionDefinition => return new ScalaHighlightExitPointsHandler(d, editor, file, element)
          case _ =>
        }
      }
      case ScalaTokenTypes.kVAL => {
        PsiTreeUtil.getParentOfType(element, classOf[ScPatternDefinition]) match {
          case pattern @ ScPatternDefinition.expr(expr) if pattern.pList.allPatternsSimple && pattern.pList.patterns.length == 1 =>
            return new ScalaHighlightExprResultHandler(expr, editor, file, element)
          case _ =>
        }
      }
      case ScalaTokenTypes.kVAR => {
        PsiTreeUtil.getParentOfType(element, classOf[ScVariableDefinition]) match {
          case pattern @ ScPatternDefinition.expr(expr) if pattern.pList.allPatternsSimple && pattern.pList.patterns.length == 1 =>
            return new ScalaHighlightExprResultHandler(expr, editor, file, element)
          case _ =>
        }
      }
      case ScalaTokenTypes.kCASE => {
        val caseClauseNullable = PsiTreeUtil.getParentOfType(element, classOf[ScCaseClause])
        for {
          cc <- Option(caseClauseNullable)
          expr <- cc.expr
        } {
          return new ScalaHighlightExprResultHandler(expr, editor, file, element)
        }
      }
      case ScalaTokenTypes.kMATCH => {
        val matchStmtNullable = PsiTreeUtil.getParentOfType(element, classOf[ScMatchStmt])
        Option(matchStmtNullable) match {
          case Some(matchStmt) =>
            return new ScalaHighlightExprResultHandler(matchStmt, editor, file, element)
          case _ =>
        }
      }
      case ScalaTokenTypes.kTRY => {
        val tryStmtNullable = PsiTreeUtil.getParentOfType(element, classOf[ScTryStmt])
        Option(tryStmtNullable) match {
          case Some(tryStmt) =>
            return new ScalaHighlightExprResultHandler(tryStmt, editor, file, element)
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
          return new ScalaHighlightExprResultHandler(body, editor, file, element)
        }
      }
      case ScalaTokenTypes.kIF => {
        val ifStmtNullable = PsiTreeUtil.getParentOfType(element, classOf[ScIfStmt])
        for {
          ifStmt <- Option(ifStmtNullable)
        } {
          return new ScalaHighlightExprResultHandler(ifStmt, editor, file, element)
        }
      }
      case ScalaTokenTypes.tFUNTYPE => {
        val funcExprNullable = PsiTreeUtil.getParentOfType(element, classOf[ScFunctionExpr])
        for {
          funcExpr <- Option(funcExprNullable)
          resultExpr <- funcExpr.result
        } {
          return new ScalaHighlightExprResultHandler(resultExpr, editor, file, element)
        }
      }
      case ScalaTokenTypes.kCLASS | ScalaTokenTypes.kTRAIT | ScalaTokenTypes.kOBJECT => {
        val templateDefOpt = PsiTreeUtil.getParentOfType(element, classOf[ScTemplateDefinition])
        for {
          templateDef <- Option(templateDefOpt)
        } {
          return new ScalaHighlightPrimaryConstructorExpressionsHandler(templateDef, editor, file, element)
        }
      }
      case _ =>
    }
    null
  }
}