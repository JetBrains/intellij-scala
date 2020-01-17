package org.jetbrains.plugins.scala
package codeInspection
package unusedInspections

import com.intellij.codeHighlighting._
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

class ScalaUnusedLocalSymbolPassFactory
  extends TextEditorHighlightingPassFactory
  with TextEditorHighlightingPassFactoryRegistrar {

  def createHighlightingPass(file: PsiFile, editor: Editor): TextEditorHighlightingPass = file match {
    case scalaFile: ScalaFile => new ScalaUnusedLocalSymbolPass(scalaFile, Option(editor.getDocument))
    case _ => null
  }

  def registerHighlightingPassFactory(registrar: TextEditorHighlightingPassRegistrar, project: Project): Unit = {
    registrar.registerTextEditorHighlightingPass(this, Array[Int](Pass.UPDATE_ALL), null, false, -1)
  }
}
