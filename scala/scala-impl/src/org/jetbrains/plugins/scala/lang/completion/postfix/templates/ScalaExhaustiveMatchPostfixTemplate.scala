package org.jetbrains.plugins.scala.lang
package completion
package postfix
package templates

import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.postfix.templates.{PostfixTemplate, StringBasedPostfixTemplate}
import com.intellij.openapi.editor.{Document, Editor}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.lang.completion.clauses.ExhaustiveMatchCompletionContributor
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScMatchStmt}
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable

final class ScalaExhaustiveMatchPostfixTemplate(alias: String = ScalaKeyword.MATCH) extends PostfixTemplate(
  null,
  alias,
  ScalaExhaustiveMatchPostfixTemplate.Example,
  null
) {

  import ScalaExhaustiveMatchPostfixTemplate._

  override def isApplicable(context: PsiElement,
                            document: Document,
                            offset: Int): Boolean =
    topMostStrategy(context).isDefined

  override def expand(context: PsiElement, editor: Editor): Unit =
    for {
      (expression, strategy) <- topMostStrategy(context)
    } expandForStrategy(expression, strategy)(context.getProject, editor)

  override def isEditable: Boolean = false
}

object ScalaExhaustiveMatchPostfixTemplate {

  import ExhaustiveMatchCompletionContributor._
  import StringBasedPostfixTemplate.{EXPR => Expr}

  private[postfix] def exhaustiveAlias = Exhaustive

  private val Example = s"$Expr ${ScalaKeyword.MATCH} $Exhaustive"

  private def topMostStrategy(context: PsiElement): Option[(ScExpression, PatternGenerationStrategy)] =
    PsiTreeUtil.getNonStrictParentOfType(context, classOf[ScExpression]) match {
      case expression@Typeable(PatternGenerationStrategy(strategy)) => Some(expression, strategy)
      case _ => None
    }

  private def expandForStrategy(expression: ScExpression,
                                strategy: PatternGenerationStrategy)
                               (implicit project: Project, editor: Editor): Unit = {
    val (components, replacement) = statementComponents(strategy)(expression)
    val templateString = s"${expression.getText} $replacement"

    removeRange(expression.getTextRange)
    startTemplate(templateString)

    for {
      element <- findElementAtCaret(expression.getContainingFile)
      statement = PsiTreeUtil.getContextOfType(element, classOf[ScMatchStmt])
      if statement != null
    } adjustTypesPhase(strategy, components, statement.caseClauses)
  }


  private[this] def removeRange(range: TextRange)
                               (implicit editor: Editor): Unit =
    editor.getDocument.deleteString(range.getStartOffset, range.getEndOffset)

  private[this] def startTemplate(templateString: String)
                                 (implicit project: Project, editor: Editor): Unit = {
    val manager = TemplateManager.getInstance(project)
    val template = manager.createTemplate("", "", templateString)
    template.setToReformat(true)
    manager.startTemplate(editor, template)
  }

  private[this] def findElementAtCaret(file: PsiFile)
                                      (implicit editor: Editor) =
    Option(file.findElementAt(editor.getCaretModel.getOffset - 1))
}