package org.jetbrains.plugins.scala
package overrideImplement

import com.intellij.psi.PsiFile
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.lang.LanguageCodeInsightActionHandler

/**
* User: Alexander Podkhalyuzin
* Date: 08.07.2008
*/

class ScalaOverrideMethodsHandler extends LanguageCodeInsightActionHandler {
  def startInWriteAction: Boolean = false

  def invoke(project: Project, editor: Editor, file: PsiFile) {
    ScalaOIUtil.invokeOverrideImplement(project, editor, file, false)
  }

  def isValidFor(editor: Editor, file: PsiFile): Boolean = file != null && ScalaFileType.SCALA_FILE_TYPE == file.getFileType
}