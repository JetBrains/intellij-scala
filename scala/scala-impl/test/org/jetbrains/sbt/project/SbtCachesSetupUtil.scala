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
  def setupCoursierAndIvyCache(project: Project): Unit =
    appendOption(project)(asOptionsString(cacheAndRepositoryVmOptions))

  /**
   * `-Dsbt.coursier.home` / `-Dsbt.ivy.home` derived from the `TC_SBT_*` environment variables provisioned on
   * TeamCity agents; empty in local runs where the variables are not set.
   */
  def coursierAndIvyCacheVmOptions: Seq[String] =
    Seq("TC_SBT_COURSIER_HOME" -> "sbt.coursier.home", "TC_SBT_IVY_HOME" -> "sbt.ivy.home")
      .flatMap { case (envVar, prop) => sys.env.get(envVar).map(value => s"-D$prop=$value") }

  /**
   * Propagates the community/project/repositories configuration file to the tests running sbt.
   * This allows them to contact the JetBrains Maven Central repository, which avoids
   * HTTP Error 429 Too Many Requests in the CI.
   */
  def repositoryConfigVmOption: String =
    s"-Dsbt.repository.config=${(TestUtils.findCommunityRootPath / "project" / "repositories").toCanonicalPath}"

  def cacheAndRepositoryVmOptions: Seq[String] =
    coursierAndIvyCacheVmOptions :+ repositoryConfigVmOption

  /**
   * [[cacheAndRepositoryVmOptions]] plus `-Dsbt.override.build.repos=true`, which makes the build's own dependency
   * resolution (not only the launcher/boot resolution) go through the repositories file.
   *
   * ONLY for forks of repo-controlled testdata builds with no custom resolvers. Never use it on the global import
   * path: project-highlighting tests import real-world projects whose builds may need resolvers absent from
   * community/project/repositories (and its ivy repositories are bootOnly).
   */
  def cacheAndRepositoryVmOptionsWithBuildReposOverride: Seq[String] =
    cacheAndRepositoryVmOptions :+ "-Dsbt.override.build.repos=true"

  /**
   * Serializes options into a whitespace-joined string for whitespace-tokenized sinks
   * ([[SbtSettings#sbtOptions]], `SbtRunConfiguration.vmparams`), wrapping tokens that
   * contain whitespace in double quotes. Both downstream parsers strip whole-token quotes, so a quoted token
   * round-trips to a single argument. Values ending in `\` or containing `"` would break the quoting, which is
   * acceptable for the directory paths passed here.
   */
  def asOptionsString(options: Seq[String]): String =
    options.map(opt => if (opt.exists(_.isWhitespace)) "\"" + opt + "\"" else opt).mkString(" ")

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

  private def appendOption(project: Project)(opt: String): Unit = {
    val settings = SbtSettings.getInstance(project)
    val old = settings.sbtOptions
    val newOpts = if (old.nonEmpty) s"$old $opt" else opt
    settings.sbtOptions = newOpts
  }
}
