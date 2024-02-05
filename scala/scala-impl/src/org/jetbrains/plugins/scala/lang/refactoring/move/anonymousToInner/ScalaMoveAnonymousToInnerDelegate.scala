package org.jetbrains.plugins.scala.lang.refactoring.move.anonymousToInner

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiReference}
import com.intellij.refactoring.move.MoveHandlerDelegate
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition

/**
 * @note original implementation was inspired by [[com.intellij.refactoring.anonymousToInner.MoveAnonymousToInnerHandler]]
 */
class ScalaMoveAnonymousToInnerDelegate extends MoveHandlerDelegate {

  override def getActionName(elements: Array[PsiElement]): String = ScalaBundle.message("move.anonymousToInner.name")

  override def canMove(elements: Array[PsiElement], targetContainer: PsiElement, reference: PsiReference): Boolean =
    findNewTemplateDefinition(elements(0), reference).exists(_.isAnonimous)

  private def findNewTemplateDefinition(element: PsiElement, reference: PsiReference): Option[ScNewTemplateDefinition] = {
    val maybeElement = Option(reference).map(_.getElement).orElse(Option(element))
    maybeElement.flatMap(element => Option(PsiTreeUtil.getParentOfType(element, classOf[ScNewTemplateDefinition])))
  }

  override def tryToMove(element: PsiElement, project: Project, dataContext: DataContext, reference: PsiReference, editor: Editor): Boolean = {
      val canRefactor = canMove(Array(element), null, reference)
      if (canRefactor) {
        val maybeNewTemplateDefinition = findNewTemplateDefinition(element, reference)
        maybeNewTemplateDefinition.foreach(element => ScalaAnonymousToInnerHandler.invoke(project, editor, element))
        maybeNewTemplateDefinition.isDefined
      }
      else false
    }
}
