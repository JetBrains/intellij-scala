package org.jetbrains.plugins.scala.codeInspection.functionExpressions

import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.codeInspection._
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil
import com.intellij.psi.impl.source.tree.TreeElement
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil.{getParentOfType, isAncestor}
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, AbstractFixOnTwoPsiElements, PsiElementVisitorSimple, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.editor.DocumentExt
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.plugins.scala.lang.psi.types.ScTypeExt
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.jdk.CollectionConverters._

class MatchToPartialFunctionInspection extends LocalInspectionTool {

  import MatchToPartialFunctionInspection._

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitorSimple = {
    case function@ScFunctionExpr(Seq(param), Some(statement@ScMatch(ScReferenceExpression(resolved), _)))
      if resolved == param && isValid(function) =>
      registerProblem(statement, function, holder)
    case function@ScFunctionExpr(Seq(param), Some(ScBlock(statement@ScMatch(ScReferenceExpression(resolved), _))))
      if resolved == param && isValid(function) =>
      registerProblem(statement, function, holder) //if fun is last statement in block, result can be block without braces
    case statement@ScMatch(_: ScUnderscoreSection, _) if checkSameResolve(statement) =>
      registerProblem(statement, statement, holder)
    case _ =>
  }
}

object MatchToPartialFunctionInspection {

  @Nls
  private[functionExpressions] val DESCRIPTION = ScalaInspectionBundle.message("convert.match.statement.to.pattern.matching.function")

  private def isValid(function: ScFunctionExpr): Boolean =
    (function.parameters.head.typeElement.isEmpty ||
      function.`type`().toOption.zip(function.expectedType()).exists {
        case (actual, expected) => actual.equiv(expected)
      }) && checkSameResolve(function)

  private def checkSameResolve(expression: ScExpression): Boolean = {
    val arg = expression.getParent match {
      case parent childOf (_: ScArgumentExprList | _: ScInfixExpr) => parent
      case _ => expression
    }

    val call = getParentOfType(expression, classOf[MethodInvocation])
    val (refText, oldReference) = call match {
      case null => return true
      case _ if !call.argumentExpressions.contains(arg) => return true
      case ScInfixExpr(ElementText(qualifier), r, _) => (qualifier + "." + r.refName, r)
      case ScMethodCall(r: ScReferenceExpression, _) => (r.getText, r)
      case _ => return true
    }

    createExpressionWithContextFromText(refText + "{case _ => }", call.getContext, call) match {
      case ScMethodCall(ref: ScReferenceExpression, _) => ref.resolve() == oldReference.resolve()
      case _ => true
    }
  }

  private def registerProblem(statement: ScMatch, expression: ScExpression, holder: ProblemsHolder): Unit = {
    findLeftBraceOrMatchKeyword(statement).map { token =>
      val tokenOffset = if (token.elementType == ScalaTokenTypes.tLBRACE) token.startOffset else token.endOffset
      tokenOffset - expression.getTextRange.getStartOffset
    }.map(new TextRange(0, _)).foreach { range =>
      val fix = MatchToPartialFunctionQuickFix(statement, expression)

      import ProblemHighlightType.GENERIC_ERROR_OR_WARNING
      holder.registerProblem(expression, DESCRIPTION, GENERIC_ERROR_OR_WARNING, range, fix)
    }
  }

  private[this] def findLeftBraceOrMatchKeyword(statement: ScMatch): Option[PsiElement] =
    findLeftBrace(statement).orElse(findMatchKeyword(statement))

  private[this] def findLeftBrace(statement: ScMatch): Option[PsiElement] =
    statement.findFirstChildByType(ScalaTokenTypes.tLBRACE)

  private[this] def findMatchKeyword(statement: ScMatch): Option[PsiElement] =
    statement.findFirstChildByType(ScalaTokenTypes.kMATCH)

  object MatchToPartialFunctionQuickFix {

    def apply(statement: ScMatch, expression: ScExpression): LocalQuickFixOnPsiElement =
      if (expression == statement) new AbstractFixOnPsiElement(DESCRIPTION, statement) {

        override protected def doApplyFix(statement: ScMatch)
                                         (implicit project: Project): Unit =
          MatchToPartialFunctionQuickFix.doApplyFix(statement, statement)
      }
      else new AbstractFixOnTwoPsiElements(DESCRIPTION, statement, expression) {

        override protected def doApplyFix(statement: ScMatch, expression: ScExpression)
                                         (implicit project: Project): Unit =
          MatchToPartialFunctionQuickFix.doApplyFix(statement, expression)
      }

    private def doApplyFix(statement: ScMatch, expression: ScExpression)
                          (implicit project: Project): Unit = {
      val matchStmtCopy = statement.copy.asInstanceOf[ScMatch]

      val newBlock = findLeftBrace(matchStmtCopy) match {
        case Some(leftBrace) =>
          addNamingPatterns(matchStmtCopy, needNamingPattern(statement))
          matchStmtCopy.deleteChildRange(matchStmtCopy.getFirstChild, leftBrace.getPrevSibling)
          createExpressionFromText(matchStmtCopy.getText, expression)
        case None =>
          findMatchKeyword(matchStmtCopy) match {
            case Some(matchKw) =>
              addNamingPatterns(matchStmtCopy, needNamingPattern(statement))
              matchStmtCopy.deleteChildRange(matchStmtCopy.getFirstChild, matchKw)
              createExpressionFromText("{" + matchStmtCopy.getText + "\n}", expression)
            case None => return
          }
      }

      CodeEditUtil.setOldIndentation(newBlock.getNode.asInstanceOf[TreeElement], CodeEditUtil.getOldIndentation(matchStmtCopy.getNode))

      IntentionPreviewUtils.write { () =>
        expression.getParent match {
          case (argList: ScArgumentExprList) childOf (call@ScMethodCall(ElementText(invoked), _)) if argList.exprs.size == 1 =>
            val replacement = createExpressionFromText(invoked + " " + newBlock.getText, call)
            call.replace(replacement)
          case block@ScBlock(`expression`) =>
            block.replace(newBlock)
          case _ =>
            expression.replace(newBlock)
        }
        statement.getContainingFile.getViewProvider.getDocument.commit(project)
      }
    }

    private[this] def needNamingPattern(statement: ScMatch): Seq[Int] = statement match {
      case ScMatch(ScReferenceExpression(argument), _) =>
        val references = findReferences(argument)(new LocalSearchScope(statement))

        statement.clauses.zipWithIndex.collect {
          case (clause, index) if references.exists(isAncestor(clause, _, false)) => index
        }
      case _ => Seq.empty
    }

    private[this] def addNamingPatterns(statement: ScMatch, indices: Seq[Int])
                                       (implicit projectContext: ProjectContext = statement.projectContext): Unit = {
      val expression = statement.expression.getOrElse(return)
      val name = expression.getText

      val clauses = statement.clauses
      val patterns = indices.flatMap(i => clauses(i).pattern)

      patterns.collect {
        case w: ScWildcardPattern => (w, name)
        case p: ScPattern =>
          val text = p.getText.parenthesize(needParentheses(p))
          (p, name + " @ " + text)
      }.foreach {
        case (pattern, text) =>
          val replacement = createPatternFromText(text, pattern)
          pattern.replace(replacement)
      }
    }

    private[this] def findReferences(element: PsiElement)
                                    (scope: LocalSearchScope) = {
      ReferencesSearch.search(element, scope)
        .findAll().asScala
        .map(_.getElement)
    }

    private[this] def needParentheses: ScPattern => Boolean = {
      case _: ScReferencePattern |
           _: ScLiteralPattern |
           _: ScConstructorPattern |
           _: ScParenthesisedPattern |
           _: ScTuplePattern |
           _: ScStableReferencePattern => false
      case _ => true
    }
  }

}
