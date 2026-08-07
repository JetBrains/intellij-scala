package org.jetbrains.sbt.project

import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.ExternalSystemManager
import com.intellij.openapi.externalSystem.settings.{ExternalProjectSettings, ExternalSystemSettingsListenerEx}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.sbt.settings.SbtSettings

import java.util

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

  /**
   * Same as [[setupCoursierAndIvyCache]], but for tests in which there is no seam between project creation and the
   * start of the sbt import, e.g. the New Project Wizard tests: the wizard links the sbt project and schedules the
   * import in one motion inside `NewProjectUtil.createFromWizard`, before the test ever sees the `Project` instance.
   *
   * The platform fires [[ExternalSystemSettingsListenerEx#onProjectsLinked]] synchronously inside
   * `AbstractExternalSystemSettings.linkProject`, which happens strictly before the import is scheduled
   * (see [[org.jetbrains.plugins.scala.project.template.ModuleBuilderUtil.doSetupModule]]), so the sbt options are
   * guaranteed to be set before `SbtExternalSystemManager.executionSettingsFor` reads them.
   */
  def setupCoursierAndIvyCacheForNewlyLinkedSbtProjects(parentDisposable: Disposable): Unit = {
    val listener = new ExternalSystemSettingsListenerEx {
      override def onProjectsLinked(
        project: Project,
        manager: ExternalSystemManager[_, _, _, _, _],
        settings: util.Collection[_ <: ExternalProjectSettings]
      ): Unit = {
        // This method is also called for the default project instance, whose settings act as a template
        // for the "New Projects" configuration and must not be polluted.
        if (!project.isDefault && manager.getSystemId == SbtProjectSystem.Id) {
          setupCoursierAndIvyCache(project)
        }
      }
    }
    ExternalSystemSettingsListenerEx.EP_NAME.getPoint.registerExtension(listener, parentDisposable)
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
