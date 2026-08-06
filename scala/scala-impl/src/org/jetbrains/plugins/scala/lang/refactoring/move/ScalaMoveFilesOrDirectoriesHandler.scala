package org.jetbrains.plugins.scala.lang.refactoring.move

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiReference}
import com.intellij.refactoring.move.MoveCallback
import com.intellij.refactoring.move.moveFilesOrDirectories.{JavaMoveFilesOrDirectoriesHandler, MoveFilesOrDirectoriesHandler}
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaFileImpl

// Basically just a wrapper around Java implementation that calls `ScalaFileImpl.performMoveRefactoring` on move
final class ScalaMoveFilesOrDirectoriesHandler extends MoveFilesOrDirectoriesHandler {
  private lazy val javaHandler: JavaMoveFilesOrDirectoriesHandler = new JavaMoveFilesOrDirectoriesHandler

  override def canMove(elements: Array[PsiElement], targetContainer: PsiElement, reference: PsiReference): Boolean = {
    // This handler should be called before a Java one, proceed only if there are Scala elements
    val hasScalaElements = elements.exists(_.getLanguage.isKindOf(ScalaLanguage.INSTANCE))
    hasScalaElements && javaHandler.canMove(elements, targetContainer, reference)
  }

  override def adjustTargetForMove(dataContext: DataContext, targetContainer: PsiElement): PsiElement =
    javaHandler.adjustTargetForMove(dataContext, targetContainer)

  override def adjustForMove(project: Project, sourceElements: Array[PsiElement], targetElement: PsiElement): Array[PsiElement] =
    javaHandler.adjustForMove(project, sourceElements, targetElement)

  /**
   * ScalaFileImpl.performMoveRefactoring is needed to avoid fake companions
   *
   * @see SCL-22351
   * @see [[org.jetbrains.plugins.scala.lang.psi.impl.ScalaFileImpl.getClasses]]
   */
  override def doMove(project: Project, elements: Array[PsiElement], targetContainer: PsiElement, callback: MoveCallback): Unit =
    ScalaFileImpl.performMoveRefactoring(javaHandler.doMove(project, elements, targetContainer, callback))

  override def doMove(elements: Array[PsiElement], targetContainer: PsiElement): Unit =
    ScalaFileImpl.performMoveRefactoring(javaHandler.doMove(elements, targetContainer))
}
