package org.jetbrains.plugins.scala.overrideImplement

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.ScalaCodeInsightActionHandler
import org.jetbrains.plugins.scala.overrideImplement.ScalaOIUtil.invokeOverrideImplement

/**
 * Handler for "Override methods" action (Ctrl + O)
 */
class ScalaOverrideMethodsHandler extends ScalaCodeInsightActionHandler {
  override def startInWriteAction: Boolean = false

  override def invoke(project: Project, editor: Editor, file: PsiFile): Unit =
    invokeOverrideImplement(file, isImplement = false)(project, editor)
}