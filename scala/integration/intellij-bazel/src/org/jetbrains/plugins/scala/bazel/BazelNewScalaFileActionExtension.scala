package org.jetbrains.plugins.scala.bazel

import com.intellij.openapi.actionSystem.{CommonDataKeys, DataContext}
import org.jetbrains.bazel.config.BazelProjectPropertiesKt
import org.jetbrains.plugins.scala.actions.NewScalaFileActionExtension

class BazelNewScalaFileActionExtension extends NewScalaFileActionExtension {
  override def isAvailable(dataContext: DataContext): Boolean = {
    val project = CommonDataKeys.PROJECT.getData(dataContext)
    if (project == null) return false
    BazelProjectPropertiesKt.isBazelProject(project)
  }
}
