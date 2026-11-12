package org.jetbrains.sbt.project

import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.ExternalSystemManager
import com.intellij.openapi.externalSystem.settings.{ExternalProjectSettings, ExternalSystemSettingsListenerEx}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.sbt.settings.SbtSettings

import java.nio.file.Path
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
   * The repositories file of the sbt processes started by tests
   * (community/scala/scala-impl/testdata/sbt/repositories-default). The sbt launcher resolves sbt itself through it,
   * and sbt resolves the build's dependencies through its entries that aren't bootOnly, followed by the build's own
   * resolvers, so the artifacts come from the JetBrains Maven Central mirror, which avoids
   * HTTP Error 429 Too Many Requests in the CI.
   *
   * Maven Central itself follows the mirror, as a fallback for new releases, which can take up to a day to reach
   * the mirror. It is a named `maven-central-fallback` entry rather than the predefined `maven-central` one, because
   * sbt moves the predefined Maven Central repository in front of all other entries of the file
   * (see `sbt.librarymanagement.ResolverFunctions.reorganizeAppResolvers`), which would put the mirror second.
   *
   * It is a copy of the repositories file of the Scala plugin's own build (community/project/repositories), apart
   * from that entry, so that the tests don't depend on how the plugin is built.
   */
  def repositoryConfigVmOption: String =
    s"-Dsbt.repository.config=${(TestUtils.getTestDataDir / "sbt" / "repositories-default").toCanonicalPath}"

  def cacheAndRepositoryVmOptions: Seq[String] =
    coursierAndIvyCacheVmOptions :+ repositoryConfigVmOption

  /**
   * The bundled Scala plugin repository (`<plugin dist>/repo`), which hosts the sbt plugins that the Scala plugin
   * injects into imported builds (sbt-structure-extractor, sbt-idea-shell) and their org.jetbrains.scala dependencies.
   *
   * Mirrors the test fallback of `org.jetbrains.sbt.SbtUtil.getDirInPlugin`, which is not accessible from this module.
   */
  private lazy val scalaPluginBundledRepo: Path = {
    val candidates = Seq(TestUtils.findUltimateRootPath, TestUtils.findCommunityRootPath)
      .map(_ / "target" / "plugin" / "Scala" / "repo")
    candidates.find(_.exists).getOrElse {
      throw new IllegalStateException(
        s"Cannot locate the bundled Scala plugin repository, tried: ${candidates.mkString(", ")}"
      )
    }
  }

  /**
   * The repositories file paired with `-Dsbt.override.build.repos=true`
   * (community/scala/scala-impl/testdata/sbt/repositories-override-build-repos). It differs from the default one of
   * [[repositoryConfigVmOption]] in three ways:
   *  - it has an `sbt-plugin-releases` ivy entry, because under the override the meta-build (`project/`) loses the
   *    resolvers that sbt adds for sbt plugins (`sbtResolvers`, which include `sbt-plugin-releases` in sbt 1.x), while
   *    some legacy sbt plugins used by testdata builds are not published to Maven Central (and thus cannot be served
   *    by the JetBrains mirror);
   *  - it has no `maven-central-fallback` entry: sbt's coursier tries all repositories against the local cache before
   *    going to the network, so a repo1.maven.org entry would shadow the mirror whenever an artifact is already
   *    cached under the repo1 host directory, making the coursier cache paths asserted by project-structure tests
   *    depend on cache warmth. With the mirror alone the paths are deterministic (the mirror is a pull-through
   *    proxy of Maven Central, so no artifacts are lost);
   *  - it has a `scala-plugin-bundled` entry for [[scalaPluginBundledRepo]], whose machine-specific path is
   *    substituted by the sbt launcher from the system property passed in [[bundledRepoPathVmOption]]. It keeps the
   *    plugins injected by sbt shell imports resolvable: old sbt versions (verified with 1.7.2) drop build-defined
   *    resolvers under the override, including the injected `MavenCache` resolver that normally serves these
   *    plugins, while recent versions (verified with 1.12.15) preserve file-based resolvers.
   */
  private def overrideRepositoryConfigVmOption: String =
    s"-Dsbt.repository.config=${(TestUtils.getTestDataDir / "sbt" / "repositories-override-build-repos").toCanonicalPath}"

  /**
   * Fills the `${scala.plugin.test.repo.dir}` substitution in the repositories file. The value is the URI path form
   * (forward slashes, percent-encoded) so that the resulting `file://` URL is valid on Windows too.
   */
  private def bundledRepoPathVmOption: String =
    s"-Dscala.plugin.test.repo.dir=${scalaPluginBundledRepo.toUri.getRawPath.stripSuffix("/")}"

  /**
   * [[coursierAndIvyCacheVmOptions]] plus a repositories file and `-Dsbt.override.build.repos=true`, which makes the
   * build's own dependency resolution (not only the launcher/boot resolution) go through that file, so that
   * Maven Central artifacts are fetched via the JetBrains mirror. See [[overrideRepositoryConfigVmOption]].
   *
   * ONLY for repo-controlled testdata builds with no custom resolvers. Never use it on the global import
   * path: project-highlighting tests import real-world projects whose builds may need other resolvers.
   */
  def cacheAndRepositoryVmOptionsWithBuildReposOverride: Seq[String] =
    coursierAndIvyCacheVmOptions ++ repositoryVmOptionsWithBuildReposOverride

  /**
   * [[cacheAndRepositoryVmOptionsWithBuildReposOverride]] without the cache options, for sbt processes that must keep
   * using the default caches, e.g. because the tests assert the paths of the resolved artifacts.
   */
  def repositoryVmOptionsWithBuildReposOverride: Seq[String] =
    Seq(overrideRepositoryConfigVmOption, bundledRepoPathVmOption, "-Dsbt.override.build.repos=true")

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
   *
   * @param overrideBuildRepositories see [[setupCoursierAndIvyCache]]
   */
  def setupCoursierAndIvyCacheForNewlyLinkedSbtProjects(parentDisposable: Disposable, overrideBuildRepositories: Boolean = false): Unit = {
    val listener = new ExternalSystemSettingsListenerEx {
      override def onProjectsLinked(
        project: Project,
        manager: ExternalSystemManager[?, ?, ?, ?, ?],
        settings: util.Collection[? <: ExternalProjectSettings]
      ): Unit = {
        // This method is also called for the default project instance, whose settings act as a template
        // for the "New Projects" configuration and must not be polluted.
        if (!project.isDefault && manager.getSystemId == SbtProjectSystem.Id) {
          setupCoursierAndIvyCache(project, overrideBuildRepositories)
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
