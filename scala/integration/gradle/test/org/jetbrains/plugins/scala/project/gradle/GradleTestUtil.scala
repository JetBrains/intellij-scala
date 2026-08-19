package org.jetbrains.plugins.scala.project.gradle

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.scala.DependencyManagerBase.Resolver

object GradleTestUtil {
  def setupGradleHome(project: Project): Unit = {
    sys.env.get("TC_GRADLE_TEST_HOME").foreach { home =>
      GradleSettings.getInstance(project).setServiceDirectoryPath(home)
    }
  }

  /** A `repositories` block for test build scripts that prefers the JetBrains Maven Central mirror to avoid
    * HTTP Error 429 Too Many Requests from Maven Central in the CI. Maven Central proper is a fallback in case
    * the mirror is unavailable, mirroring the resolver order in community/project/repositories. */
  val repositoriesBlock: String =
    s"""repositories {
       |    maven {
       |        name = 'JetBrains Maven Central Mirror'
       |        url = '${Resolver.JetBrainsMavenCentralMirror.root}'
       |    }
       |    mavenCentral()
       |}""".stripMargin

  /** A `pluginManagement` block for test settings scripts that prefers the JetBrains mirror of the
    * Gradle Plugin Portal, with the Gradle Plugin Portal proper as a fallback. Must come first in
    * settings.gradle. */
  val pluginManagementBlock: String =
    """pluginManagement {
      |    repositories {
      |        maven {
      |            name = 'JetBrains Gradle Plugin Portal Mirror'
      |            url = 'https://cache-redirector.jetbrains.com/plugins.gradle.org/m2'
      |        }
      |        gradlePluginPortal()
      |    }
      |}""".stripMargin
}
