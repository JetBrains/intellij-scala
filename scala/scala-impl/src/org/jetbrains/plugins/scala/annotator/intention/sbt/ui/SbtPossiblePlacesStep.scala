package org.jetbrains.plugins.scala.annotator.intention.sbt.ui

import javax.swing.{Icon, JComponent}

import com.intellij.ide.wizard.Step
import com.intellij.openapi.project.Project
import com.intellij.util.ui.JBUI
import org.jetbrains.plugins.scala.annotator.intention.sbt.DependencyPlaceInfo

/**
  * Created by afonichkin on 7/19/17.
  */
class SbtPossiblePlacesStep(wizard: SbtArtifactSearchWizard, project: Project, fileLines: Seq[DependencyPlaceInfo])
  extends SbtPossiblePlacesPanel(project, wizard, fileLines) with Step {

  override def _init(): Unit = {
    wizard.setTitle("Place to add dependency")
    wizard.setSize(JBUI.scale(600), JBUI.scale(750))
    updateUI()
  }

  override def getComponent: JComponent = this

  override def _commit(finishChosen: Boolean): Unit = {
    if (finishChosen) {
      wizard.resultFileLine = getResult
    }

    releaseEditor()
  }

  override def getIcon: Icon = null

  override def getPreferredFocusedComponent: JComponent = this
}
