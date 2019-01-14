package org.jetbrains.plugins.scala.lang.formatting.processors

import com.intellij.openapi.util.TextRange
import com.intellij.psi.codeStyle.{CodeStyleManager, CodeStyleSettings, CommonCodeStyleSettings}
import com.intellij.psi.impl.source.SourceTreeToPsiMap
import com.intellij.psi.impl.source.codeStyle.{CodeEditUtil, PostFormatProcessorHelper}
import com.intellij.psi.{PsiElement, PsiFile, PsiWhiteSpace}
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText

/**
 * @author Alexander Podkhalyuzin
 */

class ScalaBraceEnforcer(settings: CodeStyleSettings) extends ScalaRecursiveElementVisitor {
  private val commonSetttings = settings.getCommonSettings(ScalaLanguage.INSTANCE)
  private val myPostProcessor: PostFormatProcessorHelper = new PostFormatProcessorHelper(commonSetttings)
  private val scalaSettings = settings.getCustomSettings(classOf[ScalaCodeStyleSettings])

  override def visitIfStatement(stmt: ScIf) {
    if (checkElementContainsRange(stmt)) {
      super.visitIfStatement(stmt)
      stmt.thenExpression match {
        case Some(thenBranch) =>
          processExpression(thenBranch, stmt, commonSetttings.IF_BRACE_FORCE)
        case _ =>
      }
      stmt.elseExpression match {
        case Some(_: ScIf) if commonSetttings.SPECIAL_ELSE_IF_TREATMENT =>
        case Some(el) =>
          processExpression(el, stmt, commonSetttings.IF_BRACE_FORCE)
        case _ =>
      }
    }
  }

  override def visitWhileStatement(ws: ScWhile) {
    if (checkElementContainsRange(ws)) {
      super.visitWhileStatement(ws)
      ws.body match {
        case Some(b) =>
          processExpression(b, ws, commonSetttings.WHILE_BRACE_FORCE)
        case _ =>
      }
    }
  }

  override def visitDoStatement(stmt: ScDo) {
    if (checkElementContainsRange(stmt)) {
      super.visitDoStatement(stmt)
      stmt.body match {
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


  override def visitTryExpression(tryStmt: ScTry) {
    if (checkElementContainsRange(tryStmt)) {
      super.visitTryExpression(tryStmt)
      tryStmt.tryBlock.children.toList.filter(!_.isInstanceOf[PsiWhiteSpace]).tail match {
        case tryChildren if tryChildren.nonEmpty => processExpressions(tryChildren, tryStmt, scalaSettings.TRY_BRACE_FORCE)
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
      case _: ScBlockExpr =>
      case c: ScBlock if c.firstChild.exists(_.isInstanceOf[ScBlockExpr]) && c.firstChild == c.lastChild =>
      case _ =>
        if (option == CommonCodeStyleSettings.FORCE_BRACES_ALWAYS ||
          (option == CommonCodeStyleSettings.FORCE_BRACES_IF_MULTILINE &&
            PostFormatProcessorHelper.isMultiline(stmt))) {
          replaceExprsWithBlock(expr)
        }
    }
  }

  private def processExpressions(elements: Seq[PsiElement], stmt: PsiElement, option: Int) {
    if (elements.size == 1 && elements.head.isInstanceOf[ScBlockExpr]) return
    if (elements.head.getNode.getElementType != ScalaTokenTypes.tLBRACE ||
      elements.last.getNode.getElementType != ScalaTokenTypes.tRBRACE) {
      if (option == CommonCodeStyleSettings.FORCE_BRACES_ALWAYS ||
        (option == CommonCodeStyleSettings.FORCE_BRACES_IF_MULTILINE &&
          PostFormatProcessorHelper.isMultiline(stmt))) {
        replaceExprsWithBlock(elements:_*)
      }
    }
  }

  private def replaceExprsWithBlock(elements: PsiElement*): Unit = {
    assert(elements.nonEmpty && elements.forall(_.isValid))
    if (!elements.forall(checkElementContainsRange)) return

    val head = elements.head
    val parent = head.getParent
    val oldTextLength: Int = parent.getTextLength
    try {
      val project = head.getProject
      val concatText = "{\n" + elements.tail.foldLeft(head.getText)((res, expr) => res + "\n"+ expr.getText) + "\n}"
      val newExpr = createExpressionFromText(concatText)(head.getManager)
      val prev = head.getPrevSibling
      if (ScalaPsiUtil.isLineTerminator(prev) || prev.isInstanceOf[PsiWhiteSpace]) {
        CodeEditUtil.removeChild(SourceTreeToPsiMap.psiElementToTree(parent), SourceTreeToPsiMap.psiElementToTree(prev))
      }
      Option(elements.last.getNextSibling) match {
        case Some(next) if ScalaPsiUtil.isLineTerminator(next) || next.isInstanceOf[PsiWhiteSpace] =>
          CodeEditUtil.removeChild(SourceTreeToPsiMap.psiElementToTree(parent), SourceTreeToPsiMap.psiElementToTree(next))
        case _ =>
      }
      for (expr <- elements.tail) {
        CodeEditUtil.removeChild(SourceTreeToPsiMap.psiElementToTree(parent), SourceTreeToPsiMap.psiElementToTree(expr))
      }
      CodeEditUtil.replaceChild(SourceTreeToPsiMap.psiElementToTree(parent),
        SourceTreeToPsiMap.psiElementToTree(head),
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
