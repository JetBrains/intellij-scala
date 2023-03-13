package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy

import com.intellij.codeHighlighting._
import com.intellij.codeInsight.daemon.impl.FileStatusMap
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

final class ScalaAccessCanBeTightenedPassFactory
  extends TextEditorHighlightingPassFactory
    with TextEditorHighlightingPassFactoryRegistrar {

  override def createHighlightingPass(file: PsiFile, editor: Editor): TextEditorHighlightingPass = {
    //optimization similar to com.intellij.codeInsight.daemon.impl.GeneralHighlightingPassFactory.createHighlightingPass
    val dirtyRange = FileStatusMap.getDirtyTextRange(editor, Pass.UPDATE_ALL)
    val nothingChangedInFile = dirtyRange == null
    if (nothingChangedInFile)
      null
    else file match {
      case scalaFile: ScalaFile =>
        new ScalaAccessCanBeTightenedPass(scalaFile, Option(editor.getDocument))
      case _ =>
        null
    }
  }

  override def registerHighlightingPassFactory(registrar: TextEditorHighlightingPassRegistrar, project: Project): Unit = {
    registrar.registerTextEditorHighlightingPass(this, Array[Int](Pass.UPDATE_ALL), null, false, -1)
  }
}
