package org.jetbrains.plugins.scala.reposearch.common

import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.repository.search.completion.api.DependencyCompletionContributor

trait ScalaDependencyCompletionContributor extends DependencyCompletionContributor {
  override def getBuildSystemId: ProjectSystemId =
    ScalaDependencyCompletionContributor.ScalaDependencyCompletionProjectSystemId
}

object ScalaDependencyCompletionContributor {
  private[common] val ScalaDependencyCompletionProjectSystemId: ProjectSystemId = new ProjectSystemId("ScalaDependencyCompletion")
}
