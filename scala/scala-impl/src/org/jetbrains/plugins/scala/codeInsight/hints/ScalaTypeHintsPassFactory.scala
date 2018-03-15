package org.jetbrains.plugins.scala
package codeInsight
package hints

import com.intellij.codeHighlighting.{TextEditorHighlightingPass, TextEditorHighlightingPassFactory, TextEditorHighlightingPassRegistrar}
import com.intellij.codeInsight.hints.ParameterHintsPassFactory.getCurrentModificationStamp
import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.editor.{Editor, EditorFactory}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

class ScalaTypeHintsPassFactory(project: Project, registrar: TextEditorHighlightingPassRegistrar)
  extends AbstractProjectComponent(project)
    with TextEditorHighlightingPassFactory {

  registrar.registerTextEditorHighlightingPass(this, null, null, false, -1)

  override def createHighlightingPass(file: PsiFile, editor: Editor): TextEditorHighlightingPass = file match {
    case scalaFile: ScalaFile if ScalaTypeHintsPassFactory(editor) != getCurrentModificationStamp(scalaFile) => new ScalaTypeHintsPass(editor, scalaFile)
    case _ => null
  }
}

object ScalaTypeHintsPassFactory {

  import java.lang.{Long => JLong}

  private[this] val LastTypePassModificationTimeStamp =
    Key.create[JLong]("LAST_TYPE_PASS_MODIFICATION_TIMESTAMP")

  private def apply(editor: Editor) =
    LastTypePassModificationTimeStamp.get(editor, 0l)

  def forceHintsUpdateOnNextPass(): Unit =
    EditorFactory.getInstance()
      .getAllEditors
      .foreach(putCurrentModificationStamp(_))

  def putCurrentModificationStamp(editor: Editor, maybeFile: Option[PsiFile] = None): Unit = {
    val maybeStamp = maybeFile.map[JLong](getCurrentModificationStamp)
    editor.putUserData(LastTypePassModificationTimeStamp, maybeStamp.orNull)
  }
}
