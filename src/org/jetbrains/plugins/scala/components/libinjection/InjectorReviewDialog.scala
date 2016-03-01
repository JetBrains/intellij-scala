package org.jetbrains.plugins.scala.components.libinjection

import java.awt.event.{ActionEvent, ActionListener}
import java.awt.{BorderLayout, Button, CardLayout}
import javax.swing.{JComponent, JDialog, JPanel}

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vfs.VirtualFileManager

/**
  * Created by mucianm on 01.03.16.
  */
class InjectorReviewDialog(project: Project, manifest: LibraryInjectorLoader#AttributedManifest) extends DialogWrapper(project, false) {

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
    val layout = new CardLayout()
    val editorsPanel = new JPanel(layout)
    panel.add(editorsPanel, BorderLayout.CENTER)
    editors.headOption.map(e=>editorsPanel.add(e.getComponent))
    val nextbutton = new Button("Next")
    nextbutton.addActionListener(new ActionListener {
      override def actionPerformed(actionEvent: ActionEvent): Unit = layout.next(editorsPanel)
    })
    val prevbutton = new Button("Next")
    prevbutton.addActionListener(new ActionListener {
      override def actionPerformed(actionEvent: ActionEvent): Unit = layout.next(editorsPanel)
    })
    panel.add(prevbutton, BorderLayout.PAGE_START)
    panel
  }

  override def dispose(): Unit = {
    editors.foreach(e => if (!e.isDisposed) EditorFactory.getInstance().releaseEditor(e))
    super.dispose()
  }
}
