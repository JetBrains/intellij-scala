package org.jetbrains.sbt

import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.scala.buildinfo.BuildInfo
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.project.Version

import javax.swing.Icon

object Sbt {
  @NonNls val Name = "sbt"

  @NonNls val Extension = ".sbt"

  @NonNls val BuildFile = "build.sbt"

  @NonNls val PropertiesFile = "build.properties"

  @NonNls val ProjectDirectory = "project"

  @NonNls val PluginsFile = "plugins.sbt"

  @NonNls val TargetDirectory = "target"

  @NonNls val ModulesDirectory = ".idea/modules"

  val ProjectDescription: String = SbtBundle.message("sbt.project.description")

  val ProjectLongDescription: String = SbtBundle.message("sbt.project.long.description")

  @NonNls val BuildModuleSuffix = "-build"

  val BuildModuleName: String = SbtBundle.message("sbt.build.module.name")

  val BuildModuleDescription: String = SbtBundle.message("sbt.build.module.description")

  @NonNls val BuildLibraryPrefix = "sbt-"

  @NonNls val UnmanagedLibraryName = "unmanaged-jars"

  @NonNls val UnmanagedSourcesAndDocsName = "unmanaged-sources-and-docs"

  @NonNls val DefinitionHolderClasses = Seq("sbt.Plugin", "sbt.Build")

  // this should be in sync with sbt.BuildUtil.baseImports
  @NonNls val DefaultImplicitImports = Seq("sbt._", "Process._", "Keys._", "dsl._")

  val LatestVersion: Version = Version(BuildInfo.sbtLatestVersion)
  val Latest_1_0: Version = Version(BuildInfo.sbtLatest_1_0)
  val Latest_0_12: Version = Version(BuildInfo.sbtLatest_0_12)
  val Latest_0_13: Version = Version(BuildInfo.sbtLatest_0_13)

  /**
   * '''ATTENTION!'''<br>
   * Don't do these icons `val`. They are initialized  in test suites fields (e.g. via Sbt.LatestVersion)
   * This can lead to initialization of [[com.github.benmanes.caffeine.cache.Caffeine]] which under the hood
   * initializes ForkJoinPool via `getExecutor` method call. This initialization is done before test initialization
   * which leads to ignoring of [[com.intellij.concurrency.IdeaForkJoinWorkerThreadFactory]].
   *
   * '''Note!''' <br>
   * When tests are run using sbt with filtering of category (using `--exclude-categories=`), all the filtered
   * tests are actually initialized (only constrictor). So if some ignored test contains a field with a reference
   * to Icon, it will lead to corrupted ForkJoinPool in non-ignored tests.
   *
   * @see [[com.intellij.concurrency.IdeaForkJoinWorkerThreadFactory]]
   * @see [[com.intellij.concurrency.PoisonFactory]]
   */
  def Icon: Icon = Icons.SBT
  def FolderIcon: Icon = Icons.SBT_FOLDER
}
