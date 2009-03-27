package org.jetbrains.plugins.scala.codeInspection.importInspections

import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory

/**
* User: Alexander Podkhalyuzin
* Date: 07.07.2008
*/

class ScalaAddImportPassFactory extends TextEditorHighlightingPassFactory {
  def createHighlightingPass(file: PsiFile, editor: Editor): TextEditorHighlightingPass = {
    if (file.getManager.isInProject(file)) {
      new ScalaAddImportPass(file, editor)
    } else null
  }
  def projectOpened {
  }
  def projectClosed {
  }
  def getComponentName = "Scala add missing imports factory"
  def initComponent {
  }
  def disposeComponent {
  }
}