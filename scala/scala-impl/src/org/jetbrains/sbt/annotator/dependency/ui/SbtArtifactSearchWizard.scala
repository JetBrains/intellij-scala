package org.jetbrains.sbt.annotator.dependency.ui

import com.intellij.ide.wizard.{AbstractWizard, Step}
import com.intellij.openapi.project.Project
import org.jetbrains.sbt.annotator.dependency.DependencyPlaceInfo
import org.jetbrains.sbt.resolvers.ArtifactInfo

/**
  * Created by afonichkin on 7/18/17.
  */
class SbtArtifactSearchWizard(project: Project, artifactInfoSet: Set[ArtifactInfo], fileLines: Seq[DependencyPlaceInfo])
  extends AbstractWizard[Step]("", project) {

  private val sbtArtifactSearchStep = new SbtArtifactChooseDependencyStep(this, artifactInfoSet)
  private val sbtPossiblePlacesStep = new SbtPossiblePlacesStep(this, project, fileLines)

  var resultArtifact: Option[ArtifactInfo] = _
  var resultFileLine: Option[DependencyPlaceInfo] = _

  override def getHelpID: String = null

  def search(): (Option[ArtifactInfo], Option[DependencyPlaceInfo]) = {
    if (!showAndGet()) {
      return (None, None)
    }
    (resultArtifact, resultFileLine)
  }


  addStep(sbtArtifactSearchStep)
  addStep(sbtPossiblePlacesStep)
  init()

  override def dispose(): Unit = {
    sbtPossiblePlacesStep.panel.releaseEditor()
    super.dispose()
  }
}
