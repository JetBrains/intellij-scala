package org.jetbrains.plugins.scala
package overrideImplement

import com.intellij.lang.LanguageCodeInsightActionHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

/**
* User: Alexander Podkhalyuzin
* Date: 08.07.2008
*/

class ScalaOverrideMethodsHandler extends LanguageCodeInsightActionHandler {
  def startInWriteAction: Boolean = false

  def invoke(project: Project, editor: Editor, file: PsiFile) {
    ScalaOIUtil.invokeOverrideImplement(project, editor, file, isImplement = false)
  }

  def isValidFor(editor: Editor, file: PsiFile): Boolean = file != null && ScalaFileType.INSTANCE == file.getFileType
}