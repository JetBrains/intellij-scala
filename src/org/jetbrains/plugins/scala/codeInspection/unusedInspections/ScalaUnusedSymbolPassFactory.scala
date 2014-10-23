package org.jetbrains.plugins.scala
package codeInspection
package unusedInspections

import com.intellij.codeHighlighting.{Pass, TextEditorHighlightingPass, TextEditorHighlightingPassFactory, TextEditorHighlightingPassRegistrar}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

class ScalaUnusedSymbolPassFactory(project: Project)
        extends TextEditorHighlightingPassFactory {
  TextEditorHighlightingPassRegistrar.getInstance(project).
    registerTextEditorHighlightingPass(this, Array[Int](Pass.UPDATE_ALL), null, false, -1)

  def projectClosed() {}

  def projectOpened() {}

  def createHighlightingPass(file: PsiFile, editor: Editor): TextEditorHighlightingPass = {
    new ScalaUnusedSymbolPass(file, editor)
  }

  def initComponent() {}

  def disposeComponent() {}

  def getComponentName: String = "Scala Unused symbol pass factory"
}