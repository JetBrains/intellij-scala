package org.jetbrains.plugins.scala.codeInspection.unusedInspections

import com.intellij.codeHighlighting.{Pass, TextEditorHighlightingPass, TextEditorHighlightingPassFactory, TextEditorHighlightingPassRegistrar}
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

/**
  * Created by Svyatoslav Ilinskiy on 11.07.16.
  */
class ScalaLocalVarCouldBeValPassFactory(project: Project) extends ProjectComponent with TextEditorHighlightingPassFactory {
  TextEditorHighlightingPassRegistrar.getInstance(project).
    registerTextEditorHighlightingPass(this, Array[Int](Pass.UPDATE_ALL), null, false, -1)

  def createHighlightingPass(file: PsiFile, editor: Editor): TextEditorHighlightingPass = file match {
    case scalaFile: ScalaFile => new ScalaLocalVarCouldBeValPass(scalaFile, Option(editor.getDocument))
    case _ => null
  }

  override def getComponentName: String = "Scala Local Var Could Be Var Factory"
}
