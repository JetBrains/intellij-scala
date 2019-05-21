package org.jetbrains.plugins.scala.lang.formatting.processors

import com.intellij.openapi.util.TextRange
import com.intellij.psi.codeStyle.{CodeStyleManager, CodeStyleSettings, CommonCodeStyleSettings}
import com.intellij.psi.impl.source.SourceTreeToPsiMap
import com.intellij.psi.impl.source.codeStyle.{CodeEditUtil, PostFormatProcessorHelper}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiFile, PsiWhiteSpace}
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText

class ScalaBraceEnforcer(settings: CodeStyleSettings, scalaSettings: ScalaCodeStyleSettings) extends ScalaRecursiveElementVisitor {
  private val commonSettings = settings.getCommonSettings(ScalaLanguage.INSTANCE)
  private val myPostProcessor = new PostFormatProcessorHelper(commonSettings)

  override def visitIf(stmt: ScIf) {
    if (checkElementContainsRange(stmt)) {
      super.visitIf(stmt)
      stmt.thenExpression match {
        case Some(thenBranch) =>
          processExpression(thenBranch, stmt, commonSettings.IF_BRACE_FORCE)
        case _ =>
      }
      stmt.elseExpression match {
        case Some(_: ScIf) if commonSettings.SPECIAL_ELSE_IF_TREATMENT =>
        case Some(el) =>
          processExpression(el, stmt, commonSettings.IF_BRACE_FORCE)
        case _ =>
      }
    }
  }

  override def visitWhile(ws: ScWhile) {
    if (checkElementContainsRange(ws)) {
      super.visitWhile(ws)
      ws.expression match {
        case Some(b) =>
          processExpression(b, ws, commonSettings.WHILE_BRACE_FORCE)
        case _ =>
      }
    }
  }

  override def visitDo(stmt: ScDo) {
    if (checkElementContainsRange(stmt)) {
      super.visitDo(stmt)
      stmt.body match {
        case Some(e) => processExpression(e, stmt, commonSettings.DOWHILE_BRACE_FORCE)
        case _ =>
      }
    }
  }

  override def visitFor(expr: ScFor) {
    if (checkElementContainsRange(expr)) {
      super.visitFor(expr)
      expr.body match {
        case Some(body) => processExpression(body, expr, commonSettings.FOR_BRACE_FORCE)
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


  override def visitTry(tryStmt: ScTry) {
    if (checkElementContainsRange(tryStmt)) {
      super.visitTry(tryStmt)

      for (tryExpr <- tryStmt.expression)
        processExpression(tryExpr, tryStmt, scalaSettings.TRY_BRACE_FORCE)

      for (fin <- tryStmt.finallyBlock; expr <- fin.expression)
        processExpression(expr, tryStmt, scalaSettings.FINALLY_BRACE_FORCE)
    }
  }


  override def visitCaseClause(cc: ScCaseClause) {
    if (checkElementContainsRange(cc)) {
      super.visitCaseClause(cc)
      cc.expr match {
        // lambdas that are already wrapped with braces and use `case` clause do not need extra braces
        case Some(expr) if !cc.getParent.nullSafe.map(_.getParent).get.isInstanceOf[ScBlockExpr] =>
          processExpression(expr, cc, scalaSettings.CASE_CLAUSE_BRACE_FORCE)
        case _ =>
      }
    }
  }


  override def visitFunctionExpression(stmt: ScFunctionExpr) {
    if (checkElementContainsRange(stmt)) {
      super.visitFunctionExpression(stmt)
      stmt.result match {
          // lambdas that are already wrapped with braces do not need extra braces
        case Some(expr) if !stmt.getParent.isInstanceOf[ScBlockExpr] =>
          processExpression(expr, stmt, scalaSettings.CLOSURE_BRACE_FORCE)
        case _ =>
      }
    }
  }

  private def processExpression(expr: ScExpression, stmt: PsiElement, option: Int) {
    expr match {
      case _: ScBlockExpr =>
      case c: ScBlock if c.firstChild.exists(_.isInstanceOf[ScBlockExpr]) && c.firstChild == c.lastChild =>
      case _ =>
        val needBraces = option == CommonCodeStyleSettings.FORCE_BRACES_ALWAYS ||
          (option == CommonCodeStyleSettings.FORCE_BRACES_IF_MULTILINE && PostFormatProcessorHelper.isMultiline(stmt))
        if (needBraces) {
          replaceElementsWithBlock(expr)
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
        replaceElementsWithBlock(elements:_*)
      }
    }
  }

  private def replaceElementsWithBlock(elements: PsiElement*): Unit = {
    assert(elements.nonEmpty && elements.forall(_.isValid))
    if (!elements.forall(checkElementContainsRange)) return

    val head :: tail = elements.toList
    val parent = head.getParent
    val oldTextLength: Int = parent.getTextLength
    try {
      val project = head.getProject
      val concatText = elements.map(_.getText).mkString("{\n", "\n", "\n}")
      val newExpr = createExpressionFromText(concatText)(head.getManager)

      val prev = PsiTreeUtil.prevLeaf(head)
      val next = PsiTreeUtil.nextLeaf(elements.last)

      def parentNode = SourceTreeToPsiMap.psiElementToTree(parent)

      def remove(child: PsiElement): Unit =
        CodeEditUtil.removeChild(parentNode, SourceTreeToPsiMap.psiElementToTree(child))

      if (prev.isInstanceOf[PsiWhiteSpace]) remove(prev)
      if (next.isInstanceOf[PsiWhiteSpace]) remove(next)

      for (expr <- tail) {
        remove(expr)
      }

      CodeEditUtil.replaceChild(
        parentNode,
        SourceTreeToPsiMap.psiElementToTree(head),
        SourceTreeToPsiMap.psiElementToTree(newExpr)
      )
      CodeStyleManager.getInstance(project).reformat(parent, true)
    } finally {
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
