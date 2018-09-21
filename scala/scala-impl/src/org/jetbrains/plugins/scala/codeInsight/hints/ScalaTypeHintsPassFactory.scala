package org.jetbrains.plugins.scala
package codeInsight
package hints

import com.intellij.codeHighlighting.{TextEditorHighlightingPass, TextEditorHighlightingPassFactory, TextEditorHighlightingPassRegistrar}
import com.intellij.codeInsight.hints.ModificationStampHolder
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

class ScalaTypeHintsPassFactory(project: Project, registrar: TextEditorHighlightingPassRegistrar)
  extends ProjectComponent with TextEditorHighlightingPassFactory {

  registrar.registerTextEditorHighlightingPass(this, null, null, false, -1)

  import ScalaTypeHintsPassFactory.StampHolder

  override def createHighlightingPass(file: PsiFile, editor: Editor): TextEditorHighlightingPass = file match {
    case scalaFile: ScalaFile if !editor.isOneLineMode && !StampHolder.isNotChanged(editor, file) => new ScalaTypeHintsPass(scalaFile, editor, StampHolder)
    case _ => null
  }
}

object ScalaTypeHintsPassFactory {

  private[hints] val StampHolder = new ModificationStampHolder(
    Key.create("LAST_TYPE_PASS_MODIFICATION_TIMESTAMP")
  )
}
