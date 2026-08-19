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
    setupCoursierAndIvyCache(project, overrideBuildRepositories = false)

  /**
   * @param overrideBuildRepositories when `true`, additionally passes `-Dsbt.override.build.repos=true` so that the
   *                                  build's own dependency resolution also goes through a repositories file.
   *                                  See the warning on [[cacheAndRepositoryVmOptionsWithBuildReposOverride]].
   */
  def setupCoursierAndIvyCache(project: Project, overrideBuildRepositories: Boolean): Unit = {
    val options =
      if (overrideBuildRepositories) cacheAndRepositoryVmOptionsWithBuildReposOverride
      else cacheAndRepositoryVmOptions
    appendOption(project)(asOptionsString(options))
  }

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
   * The repositories file paired with `-Dsbt.override.build.repos=true`. It differs from
   * community/project/repositories in two ways:
   *  - it has a non-bootOnly `sbt-plugin-releases` ivy entry, because under the override the bootOnly entries are
   *    excluded from build resolution while some legacy sbt plugins used by testdata builds are not published to
   *    Maven Central (and thus cannot be served by the JetBrains mirror);
   *  - it has no direct `maven-central` entry: sbt's coursier tries all repositories against the local cache before
   *    going to the network, so a repo1.maven.org entry would shadow the mirror whenever an artifact is already
   *    cached under the repo1 host directory, making the coursier cache paths asserted by project-structure tests
   *    depend on cache warmth. With the mirror alone the paths are deterministic (the mirror is a pull-through
   *    proxy of Maven Central, so no artifacts are lost).
   */
  private def overrideRepositoryConfigVmOption: String =
    s"-Dsbt.repository.config=${(TestUtils.getTestDataDir / "sbt" / "repositories").toCanonicalPath}"

  /**
   * [[coursierAndIvyCacheVmOptions]] plus a repositories file and `-Dsbt.override.build.repos=true`, which makes the
   * build's own dependency resolution (not only the launcher/boot resolution) go through that file, so that
   * Maven Central artifacts are fetched via the JetBrains mirror. See [[overrideRepositoryConfigVmOption]].
   *
   * ONLY for repo-controlled testdata builds with no custom resolvers. Never use it on the global import
   * path: project-highlighting tests import real-world projects whose builds may need other resolvers.
   */
  def cacheAndRepositoryVmOptionsWithBuildReposOverride: Seq[String] =
    coursierAndIvyCacheVmOptions ++ Seq(overrideRepositoryConfigVmOption, "-Dsbt.override.build.repos=true")

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
