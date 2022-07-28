package org.jetbrains.plugins.scala
package overrideImplement

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.overrideImplement.ScalaOIUtil.invokeOverrideImplement

class ScalaImplementMethodsHandler extends ScalaCodeInsightActionHandler {
  override def startInWriteAction: Boolean = false

  override def invoke(project: Project, editor: Editor, file: PsiFile): Unit =
    invokeOverrideImplement(file, isImplement = true)(project, editor)
}