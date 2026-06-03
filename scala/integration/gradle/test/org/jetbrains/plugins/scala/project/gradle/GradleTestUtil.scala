package org.jetbrains.plugins.scala.project.gradle

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.settings.GradleSettings

object GradleTestUtil {
  def setupGradleHome(project: Project): Unit = {
    sys.env.get("TC_GRADLE_TEST_HOME").foreach { home =>
      GradleSettings.getInstance(project).setServiceDirectoryPath(home)
    }
  }
}
