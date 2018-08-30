package org.jetbrains.plugins.scala.lang
package completion
package postfix
package templates

import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.postfix.templates.{PostfixTemplate, StringBasedPostfixTemplate}
import com.intellij.openapi.editor.{Document, Editor}
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.completion.clauses.ExhaustiveMatchCompletionContributor
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScMatchStmt}

final class ScalaExhaustiveMatchPostfixTemplate(alias: String = ScalaExhaustiveMatchPostfixTemplate.matchAlias) extends PostfixTemplate(
  null,
  alias,
  ScalaExhaustiveMatchPostfixTemplate.Example,
  null
) {

  import ExhaustiveMatchCompletionContributor._
  import ScalaExhaustiveMatchPostfixTemplate._

  override def isApplicable(context: PsiElement,
                            document: Document,
                            offset: Int): Boolean =
    topMostStrategy(context).isDefined

  override def expand(context: PsiElement, editor: Editor): Unit =
    topMostStrategy(context).foreach {
      case (expression, strategy) => expand(strategy, expression.getContainingFile)(expression, context.getProject, editor)
    }

  private def expand(strategy: PatternGenerationStrategy, file: PsiFile)
                    (implicit expression: ScExpression,
                     project: Project, editor: Editor): Unit = {
    val components = strategy.patterns
    val templateString = s"${expression.getText} ${statementText(components)}"

    removeExpression(expression)
    startTemplate(templateString)

    for {
      element <- findElementAtCaret(file)
      statement <- element.findContextOfType(classOf[ScMatchStmt])
    } adjustTypesPhase(strategy, components, statement.caseClauses)
  }

  override def isEditable: Boolean = false
}

object ScalaExhaustiveMatchPostfixTemplate {

  import ExhaustiveMatchCompletionContributor.{Exhaustive, Match, PatternGenerationStrategy}
  import StringBasedPostfixTemplate.{EXPR => Expr}

  private[postfix] def matchAlias = Match

  private[postfix] def exhaustiveAlias = Exhaustive

  private val Example = s"$Expr $Match $Exhaustive"

  private def topMostStrategy(context: PsiElement) =
    context.parentOfType(classOf[ScExpression], strict = false).collect {
      case expression@PatternGenerationStrategy(strategy) => (expression, strategy)
    }

  private def removeExpression(expression: ScExpression)
                              (implicit editor: Editor): Unit = {
    val range = expression.getTextRange
    editor.getDocument.deleteString(range.getStartOffset, range.getEndOffset)
  }

  private def startTemplate(templateString: String)
                           (implicit project: Project, editor: Editor): Unit = {
    val manager = TemplateManager.getInstance(project)
    val template = manager.createTemplate("", "", templateString)
    template.setToReformat(true)
    manager.startTemplate(editor, template)
  }

  private def findElementAtCaret(file: PsiFile)
                                (implicit editor: Editor) =
    Option(file.findElementAt(editor.getCaretModel.getOffset - 1))
}