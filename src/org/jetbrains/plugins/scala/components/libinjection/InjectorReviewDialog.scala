package org.jetbrains.plugins.scala.components.libinjection

import java.awt.event.ActionEvent
import java.awt.{BorderLayout, CardLayout}
import javax.swing.{Action, JComponent, JPanel}

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.editor.{Editor, EditorFactory}
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.{PsiDocumentManager, PsiManager}
import org.jetbrains.plugins.scala.ScalaFileType

/**
  * Created by mucianm on 01.03.16.
  */
class InjectorReviewDialog(project: Project, manifest: LibraryInjectorLoader#AttributedManifest, LOG: Logger) extends DialogWrapper(project, false) {

  val layout = new CardLayout()
  var editorsPanel: JPanel = null
  val editors: Seq[Editor] = {
    val containingJar = manifest._1.jarPath.dropRight(1)
    val files = manifest._2
      .flatMap {
        injectorDescriptor =>
          injectorDescriptor.sources
            .flatMap {
              source =>
                val file = VirtualFileManager.getInstance().findFileByUrl(s"jar://$containingJar!/$source")
                if (file.isValid) {
                  if (file.isDirectory) file.getChildren
                  else Seq(file)
                } else {
                  LOG.warn(s"Source root '$source' is broken, check your library - $containingJar")
                  Seq.empty
                }
            }
      }
    val psiManager = PsiManager.getInstance(project)
    val psiDocumentManager = PsiDocumentManager.getInstance(project)
    val psiFiles = files.map(psiManager.findFile)
    val documents = psiFiles.map(psiDocumentManager.getDocument)
    documents.map(EditorFactory.getInstance().createViewer)
  }

  private val highlighterFactory = EditorHighlighterFactory.getInstance
  editors
    .foreach(_.asInstanceOf[EditorEx].setHighlighter(
      highlighterFactory.createEditorHighlighter(project, ScalaFileType.SCALA_FILE_TYPE))
    )
  setTitle(s"Library '${manifest._1.jarPath}' Injectors Source Review")
  setOKButtonText("Accept")
  setCancelButtonText("Reject")
  init()

  override def createCenterPanel(): JComponent = {
    val panel = new JPanel(new BorderLayout())
    editorsPanel = new JPanel(layout)
    panel.add(editorsPanel, BorderLayout.CENTER)
    editors.map(e => editorsPanel.add(e.getComponent))
    panel
  }

  override def createLeftSideActions(): Array[Action] = {
    val next = new DialogWrapperAction("Next") {
      override def doAction(e: ActionEvent): Unit = layout.next(editorsPanel)
    }
    val prev = new DialogWrapperAction("Prev") {
      override def doAction(e: ActionEvent): Unit = layout.previous(editorsPanel)
    }
    Array(next, prev)
  }

  override def dispose(): Unit = {
    editors.foreach(e => if (!e.isDisposed) EditorFactory.getInstance().releaseEditor(e))
    super.dispose()
  }
}
