package org.jetbrains.plugins.scala.components.libinjection

import java.awt.event.{ActionEvent, ActionListener}
import java.awt.{BorderLayout, Button, CardLayout, ComponentOrientation}
import javax.swing.{Action, JComponent, JDialog, JPanel}

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.ui.JBCardLayout

/**
  * Created by mucianm on 01.03.16.
  */
class InjectorReviewDialog(project: Project, manifest: LibraryInjectorLoader#AttributedManifest) extends DialogWrapper(project, false) {

  val layout = new CardLayout()
  var editorsPanel: JPanel = null
  val editors = {
    val jar = manifest._1.jarPath.dropRight(1)
    manifest._2
      .flatMap(i => i.sources
        .map(s=>FileDocumentManager.getInstance().getDocument(VirtualFileManager.getInstance().findFileByUrl(s"jar://$jar!/$s"))
        )
      )
      .map(EditorFactory.getInstance().createViewer)
  }
  setTitle(s"Library '${manifest._1.jarPath}' Injectors Source Review")
  setOKButtonText("Accept")
  setCancelButtonText("Reject")
  init()

  override def createCenterPanel(): JComponent = {
    val panel = new JPanel(new BorderLayout())
    editorsPanel = new JPanel(layout)
    panel.add(editorsPanel, BorderLayout.CENTER)
    editors.map(e=>editorsPanel.add(e.getComponent))
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
