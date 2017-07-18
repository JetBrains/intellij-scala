package org.jetbrains.plugins.scala.annotator.intention.ui

import javax.swing.JComponent

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.TabbedPaneWrapper
import com.intellij.util.ui.JBUI
import org.jetbrains.sbt.resolvers.ArtifactInfo

/**
  * Created by user on 7/13/17.
  */
class SbtArtifactSearchDialog(project: Project, canBeParent: Boolean, artifactInfoSet: Set[ArtifactInfo]) extends DialogWrapper(project, canBeParent) {
  var resultArtifact: Option[ArtifactInfo] = _

  val myTabbedPane: TabbedPaneWrapper = new TabbedPaneWrapper(project)
  val myArtifactsPanel: SbtArtifactSearchPanel = new SbtArtifactSearchPanel(this, artifactInfoSet)

  init()

  override def init(): Unit = {
    initComponents()
    setTitle("Sbt Artifact Search")
    setOKActionEnabled(false)

    super.init()
  }

  def searchForArtifact(): Option[ArtifactInfo] = {
    if (!showAndGet()) {
      return None
    }

    resultArtifact
  }

  override def doOKAction(): Unit = {
    resultArtifact = myArtifactsPanel.getResult()
    super.doOKAction()
  }

  private def initComponents() = {
    myTabbedPane.addTab("Search for artifact", myArtifactsPanel)
    val editor = EditorFactory.getInstance().createViewer(FileDocumentManager.getInstance().getDocument(project.getBaseDir.findChild("build.sbt")))
    editor.offsetToXY(12)
    myTabbedPane.addTab("Editor", editor.getComponent)
    myTabbedPane.getComponent.setPreferredSize(JBUI.size(900, 600))
  }

  override def createCenterPanel(): JComponent = myTabbedPane.getComponent

}
