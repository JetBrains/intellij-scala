package org.jetbrains.plugins.scala.packagesearch.ui

import com.intellij.ide.wizard.Step
import com.intellij.openapi.project.Project
import com.intellij.ui.scale.JBUIScale
import org.jetbrains.plugins.scala.extensions
import org.jetbrains.plugins.scala.packagesearch.PackageSearchSbtBundle
import org.jetbrains.sbt.language.utils.DependencyOrRepositoryPlaceInfo

import javax.swing.{Icon, JComponent}

private class SbtPossiblePlacesStep(wizard: AddDependencyOrRepositoryPreviewWizard, project: Project, fileLines: Seq[DependencyOrRepositoryPlaceInfo])
  extends  Step {

  val panel = new SbtPossiblePlacesPanel(project, wizard, fileLines)

  override def _init(): Unit = {
    wizard.setTitle(s"""${PackageSearchSbtBundle.message("packagesearch.dependency.sbt.possible.places.to.add.new")} ${wizard.getSubject}""")
    wizard.setSize( JBUIScale.scale(800), JBUIScale.scale(750))
    panel.myResultList.clearSelection()
    extensions.inWriteAction {
      panel.myCurEditor.getDocument.setText(PackageSearchSbtBundle.message("packagesearch.dependency.sbt.select.a.place.from.the.list.above.to.enable.this.preview"))
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