package org.jetbrains.plugins.scala.annotator.gutter

import com.intellij.codeInsight.CodeInsightActionHandler
import com.intellij.psi.PsiFile
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project

/**
 * User: Alexander Podkhalyuzin
 * Date: 08.11.2008
 */

class ScalaGoToSuperActionHandler extends CodeInsightActionHandler{
  def startInWriteAction = false

  def invoke(project: Project, editor: Editor, file: PsiFile) = {
    
  }
}