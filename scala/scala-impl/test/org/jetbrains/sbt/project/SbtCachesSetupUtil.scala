package org.jetbrains.sbt.project

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.sbt.settings.SbtSettings

object SbtCachesSetupUtil {
  def setupCoursierAndIvyCache(project: Project): Unit = {
    propagateEnvVarAsSbtOption(project, "TC_SBT_COURSIER_HOME", "sbt.coursier.home")
    propagateEnvVarAsSbtOption(project, "TC_SBT_IVY_HOME", "sbt.ivy.home")

    // Propagates the community/project/repositories configuration file to the tests running sbt.
    // This allows them to contact the JetBrains Maven Central repository, which avoids
    // HTTP Error 429 Too Many Requests in the CI.
    val repositoriesFile = (TestUtils.findCommunityRootPath / "project" / "repositories").toCanonicalPath
    val repoConfig = s"-Dsbt.repository.config=$repositoriesFile"
    appendOption(project)(repoConfig)
  }

  private def propagateEnvVarAsSbtOption(project: Project, envVar: String, opt: String): Unit = {
    sys.env.get(envVar).map(p => s"-D$opt=$p").foreach(appendOption(project))
  }

  private def appendOption(project: Project)(opt: String): Unit = {
    val settings = SbtSettings.getInstance(project)
    val old = settings.sbtOptions
    val newOpts = if (old.nonEmpty) s"$old $opt" else opt
    settings.sbtOptions = newOpts
  }
}
