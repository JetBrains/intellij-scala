package org.jetbrains.plugins.scala.lang.formatting.processors

import com.intellij.openapi.util.TextRange
import com.intellij.psi.codeStyle.{CodeStyleManager, CodeStyleSettings, CommonCodeStyleSettings}
import com.intellij.psi.impl.source.SourceTreeToPsiMap
import com.intellij.psi.impl.source.codeStyle.{CodeEditUtil, PostFormatProcessorHelper}
import com.intellij.psi.{PsiElement, PsiFile, PsiWhiteSpace}
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

/**
 * @author Alexander Podkhalyuzin
 */

class ScalaBraceEnforcer(settings: CodeStyleSettings) extends ScalaRecursiveElementVisitor {
  private val myPostProcessor: PostFormatProcessorHelper = new PostFormatProcessorHelper(settings)
  private val commonSetttings = settings.getCommonSettings(ScalaFileType.SCALA_LANGUAGE)
  private val scalaSettings = settings.getCustomSettings(classOf[ScalaCodeStyleSettings])

  override def visitIfStatement(stmt: ScIfStmt) {
    if (checkElementContainsRange(stmt)) {
      super.visitIfStatement(stmt)
      stmt.thenBranch match {
        case Some(thenBranch) =>
          processExpression(thenBranch, stmt, commonSetttings.IF_BRACE_FORCE)
        case _ =>
      }
      stmt.elseBranch match {
        case Some(i: ScIfStmt) if commonSetttings.SPECIAL_ELSE_IF_TREATMENT =>
        case Some(el) =>
          processExpression(el, stmt, commonSetttings.IF_BRACE_FORCE)
        case _ =>
      }
    }
  }

  override def visitWhileStatement(ws: ScWhileStmt) {
    if (checkElementContainsRange(ws)) {
      super.visitWhileStatement(ws)
      ws.body match {
        case Some(b) =>
          processExpression(b, ws, commonSetttings.WHILE_BRACE_FORCE)
        case _ =>
      }
    }
  }

  override def visitDoStatement(stmt: ScDoStmt) {
    if (checkElementContainsRange(stmt)) {
      super.visitDoStatement(stmt)
      stmt.getExprBody match {
        case Some(e) => processExpression(e, stmt, commonSetttings.DOWHILE_BRACE_FORCE)
        case _ =>
      }
    }
  }

  override def visitForExpression(expr: ScForStatement) {
    if (checkElementContainsRange(expr)) {
      super.visitForExpression(expr)
      expr.body match {
        case Some(body) => processExpression(body, expr, commonSetttings.FOR_BRACE_FORCE)
        case _ =>
      }
    }
  }

  override def visitFunction(fun: ScFunction) {
    if (checkElementContainsRange(fun)) {
      super.visitFunction(fun)
      fun match {
        case d: ScFunctionDefinition =>
          d.body match {
            case Some(b) => processExpression(b, fun, scalaSettings.METHOD_BRACE_FORCE)
            case _ =>
          }
        case _ =>
      }
    }
  }


  override def visitTryExpression(tryStmt: ScTryStmt) {
    if (checkElementContainsRange(tryStmt)) {
      super.visitTryExpression(tryStmt)
      tryStmt.tryBlock.exprs match {
        case Seq(expr) => processExpression(expr, tryStmt, scalaSettings.TRY_BRACE_FORCE)
        case _ =>
      }
      tryStmt.finallyBlock match {
        case Some(fin) =>
          fin.expression match {
            case Some(expr) => processExpression(expr, tryStmt, scalaSettings.FINALLY_BRACE_FORCE)
            case _ =>
          }
        case _ =>
      }
    }
  }


  override def visitCaseClause(cc: ScCaseClause) {
    if (checkElementContainsRange(cc)) {
      super.visitCaseClause(cc)
      cc.expr match {
        case Some(expr) => processExpression(expr, cc, scalaSettings.CASE_CLAUSE_BRACE_FORCE)
        case _ =>
      }
    }
  }


  override def visitFunctionExpression(stmt: ScFunctionExpr) {
    if (checkElementContainsRange(stmt)) {
      super.visitFunctionExpression(stmt)
      stmt.result match {
        case Some(expr) => processExpression(expr, stmt, scalaSettings.CLOSURE_BRACE_FORCE)
        case _ =>
      }
    }
  }

  private def processExpression(expr: ScExpression, stmt: PsiElement, option: Int) {
    expr match {
      case b: ScBlockExpr =>
      case _ =>
        if (option == CommonCodeStyleSettings.FORCE_BRACES_ALWAYS ||
          (option == CommonCodeStyleSettings.FORCE_BRACES_IF_MULTILINE &&
            PostFormatProcessorHelper.isMultiline(stmt))) {
          replaceExprWithBlock(expr)
        }
    }
  }

  private def replaceExprWithBlock(expr: ScExpression) {
    assert(expr.isValid)
    if (!checkElementContainsRange(expr)) return

    val parent = expr.getParent
    val oldTextLength: Int = parent.getTextLength
    try {
      val project = expr.getProject
      val newExpr = ScalaPsiElementFactory.createExpressionFromText("{\n" + expr.getText + "\n}", expr.getManager)
      val prev = expr.getPrevSibling
      if (ScalaPsiUtil.isLineTerminator(prev) || prev.isInstanceOf[PsiWhiteSpace]) {
        CodeEditUtil.removeChild(SourceTreeToPsiMap.psiElementToTree(parent), SourceTreeToPsiMap.psiElementToTree(prev))
      }
      CodeEditUtil.replaceChild(SourceTreeToPsiMap.psiElementToTree(parent),
        SourceTreeToPsiMap.psiElementToTree(expr),
        SourceTreeToPsiMap.psiElementToTree(newExpr))
      CodeStyleManager.getInstance(project).reformat(parent, true)
    }
    finally {
      updateResultRange(oldTextLength, parent.getTextLength)
    }
  }

  protected def checkElementContainsRange(element: PsiElement): Boolean = {
    myPostProcessor.isElementPartlyInRange(element)
  }

  protected def updateResultRange(oldTextLength: Int, newTextLength: Int) {
    myPostProcessor.updateResultRange(oldTextLength, newTextLength)
  }

  def process(formatted: PsiElement): PsiElement = {
    assert(formatted.isValid)
    formatted.accept(this)
    formatted
  }

  def processText(source: PsiFile, rangeToReformat: TextRange): TextRange = {
    myPostProcessor.setResultTextRange(rangeToReformat)
    source.accept(this)
    myPostProcessor.getResultTextRange
  }
}