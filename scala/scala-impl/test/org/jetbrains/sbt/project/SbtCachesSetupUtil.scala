package org.jetbrains.sbt.project

import com.intellij.openapi.project.Project
import org.jetbrains.sbt.settings.SbtSettings

object SbtCachesSetupUtil {
  def setupCoursierAndIvyCache(project: Project): Unit = {
    propagateEnvVarAsSbtOption(project, "TC_SBT_COURSIER_HOME", "sbt.coursier.home")
    propagateEnvVarAsSbtOption(project, "TC_SBT_IVY_HOME", "sbt.ivy.home")
  }

  private def propagateEnvVarAsSbtOption(project: Project, envVar: String, opt: String): Unit = {
    sys.env.get(envVar).map(p => s"-D$opt=$p").foreach { o =>
      val settings = SbtSettings.getInstance(project)
      val old = settings.sbtOptions
      val newOpts = if (old.nonEmpty) s"$old $o" else o
      settings.sbtOptions = newOpts
    }
  }
}
