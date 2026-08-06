package org.jetbrains.plugins.scala.lang.refactoring

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiFile}
import com.intellij.refactoring.RefactoringActionHandler

trait ScalaRefactoringActionHandler extends RefactoringActionHandler {

  def invoke(file: PsiFile)
            (implicit project: Project, editor: Editor, dataContext: DataContext): Unit

  def invoke(elements: Array[PsiElement])
            (implicit project: Project, dataContext: DataContext): Unit = {}

  override final def invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext): Unit =
    invoke(file)(project, editor, dataContext)

  override def invoke(project: Project, elements: Array[PsiElement], dataContext: DataContext): Unit =
    invoke(elements)(project, dataContext)
}
