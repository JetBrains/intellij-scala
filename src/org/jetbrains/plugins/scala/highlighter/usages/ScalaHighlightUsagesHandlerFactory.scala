package org.jetbrains.plugins.scala.highlighter.usages

import com.intellij.codeInsight.TargetElementUtil
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

class ScalaHighlightUsagesHandlerFactory extends HighlightUsagesHandlerFactory {
  def createHighlightUsagesHandler(editor: Editor, file: PsiFile): HighlightUsagesHandlerBase[_ <: PsiElement] = {
    if (!file.isInstanceOf[ScalaFile]) return null
    val offset = TargetElementUtil.adjustOffset(file, editor.getDocument, editor.getCaretModel.getOffset)
    val element: PsiElement = file.findElementAt(offset)
    if (element == null || element.getNode == null) return null
    element.getNode.getElementType match {
      case ScalaTokenTypes.kRETURN =>
        val fun = PsiTreeUtil.getParentOfType(element, classOf[ScFunctionDefinition])
        if (fun != null) return new ScalaHighlightExitPointsHandler(fun, editor, file, element)
      case ScalaTokenTypes.kDEF =>
        val fun = PsiTreeUtil.getParentOfType(element, classOf[ScFunction])
        fun match {
          case d: ScFunctionDefinition => return new ScalaHighlightExitPointsHandler(d, editor, file, element)
          case _ =>
        }
      case ScalaTokenTypes.kVAL =>
        PsiTreeUtil.getParentOfType(element, classOf[ScPatternDefinition]) match {
          case pattern@ScPatternDefinition.expr(expr) if pattern.pList.simplePatterns && pattern.pList.patterns.length == 1 =>
            return new ScalaHighlightExprResultHandler(expr, editor, file, element)
          case _ =>
        }
      case ScalaTokenTypes.kVAR =>
        PsiTreeUtil.getParentOfType(element, classOf[ScVariableDefinition]) match {
          case pattern@ScVariableDefinition.expr(expr) if pattern.pList.simplePatterns && pattern.pList.patterns.length == 1 =>
            return new ScalaHighlightExprResultHandler(expr, editor, file, element)
          case _ =>
        }
      case ScalaTokenTypes.kCASE =>
        val cc = PsiTreeUtil.getParentOfType(element, classOf[ScCaseClause])
        if (cc != null) {
          cc.expr match {
            case Some(expr) =>
              return new ScalaHighlightExprResultHandler(expr, editor, file, element)
            case _ =>
          }
        }
      case ScalaTokenTypes.kMATCH =>
        val matchStmt = PsiTreeUtil.getParentOfType(element, classOf[ScMatchStmt])
        if (matchStmt != null) {
          return new ScalaHighlightExprResultHandler(matchStmt, editor, file, element)
        }
      case ScalaTokenTypes.kTRY =>
        val tryStmt = PsiTreeUtil.getParentOfType(element, classOf[ScTryStmt])
        if (tryStmt != null) {
          return new ScalaHighlightExprResultHandler(tryStmt, editor, file, element)
        }
      case ScalaTokenTypes.kFOR =>
        val forStmt = PsiTreeUtil.getParentOfType(element, classOf[ScForStatement])
        if (forStmt != null && forStmt.isYield) {
          forStmt.body match {
            case Some(body) =>
              return new ScalaHighlightExprResultHandler(body, editor, file, element)
            case _ =>
          }
        }
      case ScalaTokenTypes.kIF =>
        val ifStmt = PsiTreeUtil.getParentOfType(element, classOf[ScIfStmt])
        if (ifStmt != null) {
          return new ScalaHighlightExprResultHandler(ifStmt, editor, file, element)
        }
      case ScalaTokenTypes.tFUNTYPE =>
        val funcExpr = PsiTreeUtil.getParentOfType(element, classOf[ScFunctionExpr])
        if (funcExpr != null) {
          funcExpr.result match {
            case Some(resultExpr) =>
              return new ScalaHighlightExprResultHandler(resultExpr, editor, file, element)
            case _ =>
          }
        }
      case ScalaTokenTypes.kCLASS | ScalaTokenTypes.kTRAIT | ScalaTokenTypes.kOBJECT =>
        val templateDef = PsiTreeUtil.getParentOfType(element, classOf[ScTemplateDefinition])
        if (templateDef != null) {
          return new ScalaHighlightPrimaryConstructorExpressionsHandler(templateDef, editor, file, element)
        }
      case _ =>
    }
    null
  }
}
