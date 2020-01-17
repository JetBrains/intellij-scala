package org.jetbrains.plugins.scala.codeInspection.unusedInspections

import com.intellij.codeHighlighting.{Pass, TextEditorHighlightingPass, TextEditorHighlightingPassFactory, TextEditorHighlightingPassFactoryRegistrar, TextEditorHighlightingPassRegistrar}
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

/**
  * Created by Svyatoslav Ilinskiy on 11.07.16.
  */
class ScalaLocalVarCouldBeValPassFactory
  extends TextEditorHighlightingPassFactory
    with TextEditorHighlightingPassFactoryRegistrar {

  def createHighlightingPass(file: PsiFile, editor: Editor): TextEditorHighlightingPass = file match {
    case scalaFile: ScalaFile => new ScalaLocalVarCouldBeValPass(scalaFile, Option(editor.getDocument))
    case _ => null
  }

  def registerHighlightingPassFactory(registrar: TextEditorHighlightingPassRegistrar, project: Project): Unit = {
    registrar.registerTextEditorHighlightingPass(this, Array[Int](Pass.UPDATE_ALL), null, false, -1)
  }
}
