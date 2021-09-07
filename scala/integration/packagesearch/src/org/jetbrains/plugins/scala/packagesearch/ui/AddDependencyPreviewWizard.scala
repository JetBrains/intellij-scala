package org.jetbrains.plugins.scala.packagesearch.ui

import com.intellij.ide.wizard.{AbstractWizard, Step}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.packagesearch.PackageSearchSbtBundle
import org.jetbrains.sbt.language.utils.{DependencyOrRepositoryPlaceInfo, SbtArtifactInfo}

class AddDependencyPreviewWizard(project: Project, elem: SbtArtifactInfo, fileLines: Seq[DependencyOrRepositoryPlaceInfo])
  extends AbstractWizard[Step](PackageSearchSbtBundle.message("packagesearch.dependency.sbt.possible.places.to.add.new.dependency"), project) {

  private val sbtPossiblePlacesStep = new SbtPossiblePlacesStep(this, project, fileLines)

  val elementToAdd: Any = elem
  var resultFileLine: Option[DependencyOrRepositoryPlaceInfo] = _

  override def getHelpID: String = null

  def search(): Option[DependencyOrRepositoryPlaceInfo] = {
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