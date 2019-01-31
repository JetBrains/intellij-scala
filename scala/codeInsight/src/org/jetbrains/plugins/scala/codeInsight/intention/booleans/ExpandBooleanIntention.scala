package org.jetbrains.plugins.scala
package codeInsight
package intention
package booleans

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiDocumentManager, PsiElement}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScParenthesisedExpr, ScReturn}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable
import org.jetbrains.plugins.scala.project.ProjectContext

/**
  * @author Ksenia.Sautina
  * @since 6/29/12
  */
final class ExpandBooleanIntention extends PsiElementBaseIntentionAction {

  import ExpandBooleanIntention._

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean =
    findReturnParent(element).filter { statement =>
      val range = statement.getTextRange
      val offset = editor.getCaretModel.getOffset
      range.getStartOffset <= offset && offset <= range.getEndOffset
    }.collect {
      case ScReturn(Typeable(scType)) => scType.canonicalText
    }.contains("Boolean")

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit = {
    val statement = findReturnParent(element).filter(_.isValid)
      .getOrElse(return)

    val expressionText = statement match {
      case ScReturn(ScParenthesisedExpr(ElementText(text))) => text
      case ScReturn(ElementText(text)) => text
      case _ => return
    }

    val start = statement.getTextRange.getStartOffset

    inWriteAction {
      implicit val context: ProjectContext = project
      val replacement = createExpressionFromText(s"if ($expressionText) { return true } else { return false }")
      statement.replaceExpression(replacement, removeParenthesis = true)

      editor.getCaretModel.moveToOffset(start)
      PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)
    }
  }

  override def getText: String = "Expand boolean use to 'if else'"

  override def getFamilyName: String = FamilyName
}

object ExpandBooleanIntention {

  private[booleans] val FamilyName = "Expand Boolean"

  private def findReturnParent(element: PsiElement): Option[ScReturn] =
    element.parentOfType(classOf[ScReturn], strict = false)
}
