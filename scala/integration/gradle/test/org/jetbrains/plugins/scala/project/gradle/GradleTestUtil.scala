package org.jetbrains.plugins.scala.project.gradle

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.settings.GradleSettings

object GradleTestUtil {
  def setupGradleHome(project: Project): Unit = {
    sys.props.get("gradle.test.home") match {
      case Some(home) =>
        GradleSettings.getInstance(project).setServiceDirectoryPath(home)
      case None =>
    }
  }
}
