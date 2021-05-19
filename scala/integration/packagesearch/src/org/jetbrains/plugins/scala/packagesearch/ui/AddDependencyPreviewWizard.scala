package org.jetbrains.plugins.scala.packagesearch.ui

import com.intellij.ide.wizard.{AbstractWizard, Step}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.NlsString
import org.jetbrains.plugins.scala.packagesearch.utils.ArtifactInfo
import org.jetbrains.sbt.annotator.dependency.DependencyPlaceInfo

class AddDependencyPreviewWizard(project: Project, artifactInfo: Option[ArtifactInfo], fileLines: Seq[DependencyPlaceInfo])
  extends AbstractWizard[Step](NlsString.force(""), project) {

  private val sbtPossiblePlacesStep = new SbtPossiblePlacesStep(this, project, fileLines)

  val resultArtifact: Option[ArtifactInfo] = artifactInfo
  var resultFileLine: Option[DependencyPlaceInfo] = _

  override def getHelpID: String = null

  def search(): Option[DependencyPlaceInfo] = {
    if (!showAndGet()) {
      return None
    }
    resultFileLine
  }

  addStep(sbtPossiblePlacesStep)
  init()

  override def dispose(): Unit = {
    sbtPossiblePlacesStep.panel.releaseEditor()
    super.dispose()
  }
}