package org.jetbrains.plugins.scala
package lang
package refactoring
package introduceParameter


import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiFile}
import com.intellij.refactoring.RefactoringActionHandler

/**
 * User: Alexander Podkhalyuzin
 * Date: 11.06.2009
 */

class ScalaIntroduceParameterHndler extends RefactoringActionHandler {
  def invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext): Unit = {

  }

  def invoke(project: Project, elements: Array[PsiElement], dataContext: DataContext): Unit = {/*do nothing*/}
}