package org.jetbrains.plugins.scala.annotator.intention.sbt.ui

import com.intellij.ide.wizard.{AbstractWizard, Step}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.annotator.intention.sbt.FileLine
import org.jetbrains.sbt.resolvers.ArtifactInfo

/**
  * Created by user on 7/18/17.
  */
class SbtArtifactSearchWizard(project: Project, artifactInfoSet: Set[ArtifactInfo], fileLines: Seq[FileLine])
  extends AbstractWizard[Step]("", project) {

  val sbtArtifactSearchStep = new SbtArtifactChooseDependencyStep(this, artifactInfoSet)
  val sbtPossiblePlacesStep = new SbtPossiblePlacesStep(this, project, fileLines)

  var resultArtifact: Option[ArtifactInfo] = _
  var resultFileLine: Option[FileLine] = _

  override def init(): Unit = {
    super.init()
  }

  override def getHelpID: String = null

  def search(): (Option[ArtifactInfo], Option[FileLine]) = {
    if (!showAndGet()) {
      return (None, None)
    }

    (resultArtifact, resultFileLine)
  }

  override def canGoNext: Boolean = {
    if (getCurrentStepObject == sbtArtifactSearchStep) {
      sbtArtifactSearchStep.canGoNext
    } else {
      sbtPossiblePlacesStep.canGoNext
    }
  }

  addStep(sbtArtifactSearchStep)
  addStep(sbtPossiblePlacesStep)
  init()
}
