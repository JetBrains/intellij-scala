package org.jetbrains.plugins.scala.annotator.intention.sbt.ui

import javax.swing.{Icon, JComponent}

import com.intellij.ide.wizard.Step
import com.intellij.openapi.project.Project
import com.intellij.util.ui.JBUI
import org.jetbrains.plugins.scala.annotator.intention.sbt.DependencyPlaceInfo
import org.jetbrains.plugins.scala.extensions

/**
  * Created by afonichkin on 7/19/17.
  */
class SbtPossiblePlacesStep(wizard: SbtArtifactSearchWizard, project: Project, fileLines: Seq[DependencyPlaceInfo])
  extends  Step {

  val panel = new SbtPossiblePlacesPanel(project, wizard, fileLines)

  override def _init(): Unit = {
    wizard.setTitle("Place to add dependency")
    wizard.setSize(JBUI.scale(800), JBUI.scale(750))
    panel.myResultList.clearSelection()
    extensions.inWriteAction {
      panel.myCurEditor.getDocument.setText("// Select a place from the list above to enable this preview")
    }
    panel.updateUI()
  }

  override def getComponent: JComponent = panel

  override def _commit(finishChosen: Boolean): Unit = {
    if (finishChosen) {
      wizard.resultFileLine = Option(panel.myResultList.getSelectedValue)
    }
  }

  override def getIcon: Icon = null

  override def getPreferredFocusedComponent: JComponent = panel
}
