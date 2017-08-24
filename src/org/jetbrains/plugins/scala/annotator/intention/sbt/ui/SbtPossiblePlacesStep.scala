package org.jetbrains.plugins.scala.annotator.intention.sbt.ui

import java.awt.Dimension
import javax.swing.{Icon, JComponent}

import com.intellij.ide.wizard.Step
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.annotator.intention.sbt.FileLine

/**
  * Created by user on 7/19/17.
  */
class SbtPossiblePlacesStep(wizard: SbtArtifactSearchWizard, project: Project, fileLines: Seq[FileLine])
  extends SbtPossiblePlacesPanel(project, wizard, fileLines) with Step {

  override def _init(): Unit = {
    wizard.setTitle("Place to add dependency")
    setPreferredSize(new Dimension(1600, 1200))
    updateUI()
  }

  override def getComponent: JComponent = this

  override def _commit(finishChosen: Boolean): Unit = {
    if (finishChosen)
      wizard.resultFileLine = getResult
  }

  override def getIcon: Icon = null

  override def getPreferredFocusedComponent: JComponent = this
}
