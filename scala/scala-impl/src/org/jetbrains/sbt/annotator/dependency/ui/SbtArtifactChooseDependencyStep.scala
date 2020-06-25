package org.jetbrains.sbt.annotator.dependency.ui

import javax.swing.{Icon, JComponent}
import com.intellij.ide.wizard.Step
import com.intellij.util.ui.JBUI
import org.jetbrains.sbt.SbtBundle
import org.jetbrains.sbt.resolvers.ArtifactInfo

/**
  * Created by afonichkin on 7/19/17.
  */
private class SbtArtifactChooseDependencyStep(wizard: SbtArtifactSearchWizard, artifactInfoSet: Set[ArtifactInfo]) extends Step {

  private val panel = new SbtArtifactSearchPanel(wizard, artifactInfoSet)

  override def _init(): Unit = {
    wizard.setTitle(SbtBundle.message("sbt.artifact.search"))
    wizard.setSize(JBUI.scale(300), JBUI.scale(400))
    wizard.getContentComponent.updateUI()
  }

  override def getIcon: Icon = null

  override def _commit(finishChosen: Boolean): Unit = {
    wizard.resultArtifact = Option(panel.myResultList.getSelectedValue)
  }

  override def getPreferredFocusedComponent: JComponent = panel

  override def getComponent: JComponent = panel
}
