package org.jetbrains.plugins.scala.packagesearch.ui

import com.intellij.buildsystem.model.unified.UnifiedDependencyRepository
import com.intellij.ide.wizard.{AbstractWizard, Step}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.NlsString
import org.jetbrains.plugins.scala.packagesearch.PackageSearchSbtBundle
import org.jetbrains.sbt.language.utils.{ArtifactInfo, DependencyOrRepositoryPlaceInfo}

class AddDependencyOrRepositoryPreviewWizard(project: Project, elem: Any, fileLines: Seq[DependencyOrRepositoryPlaceInfo])
  extends AbstractWizard[Step](NlsString.force(""), project) {

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

  def getSubject: String = {
    elementToAdd match {
      case _:ArtifactInfo =>
        PackageSearchSbtBundle.message("packagesearch.dependency.sbt.dependency")
      case _: UnifiedDependencyRepository =>
        PackageSearchSbtBundle.message("packagesearch.dependency.sbt.repository")
      case _ => ""
    }
  }

  addStep(sbtPossiblePlacesStep)
  init()

  override def dispose(): Unit = {
    sbtPossiblePlacesStep.panel.releaseEditor()
    super.dispose()
  }
}