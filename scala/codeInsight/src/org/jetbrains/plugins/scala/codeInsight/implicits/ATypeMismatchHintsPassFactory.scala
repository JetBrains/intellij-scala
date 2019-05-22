package org.jetbrains.plugins.scala.codeInsight.implicits

import com.intellij.codeHighlighting._
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.annotator.TypeMismatchError
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

// TODO experimental feature (SCL-15250)
class ATypeMismatchHintsPassFactory(project: Project, registrar: TextEditorHighlightingPassRegistrar) extends ProjectComponent with TextEditorHighlightingPassFactory {
  registrar.registerTextEditorHighlightingPass(this, Array(Pass.UPDATE_ALL), null, false, -1)

  override def createHighlightingPass(file: PsiFile, editor: Editor): TextEditorHighlightingPass = file match {
    case file: ScalaFile if !ImplicitHints.isUpToDate(editor, file) =>
      new TextEditorHighlightingPass(editor.getProject, editor.getDocument, /*runIntentionPassAfter*/ false) {
        override def doCollectInformation(progress: ProgressIndicator): Unit = {}

        override def doApplyInformationToEditor(): Unit = file.elements.foreach { element =>
          if (TypeMismatchError(element).exists(_.modificationCount < element.getManager.getModificationTracker.getModificationCount)) {
            TypeMismatchError.clear(element)
          }
        }
      }
    case _ => null
  }
}
