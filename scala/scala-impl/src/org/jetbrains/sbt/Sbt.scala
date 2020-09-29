package org.jetbrains.sbt

import com.intellij.notification.NotificationGroup
import javax.swing.Icon
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.scala.buildinfo.BuildInfo
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.project.Version
import org.jetbrains.plugins.scala.util.ScalaNotificationGroups

/**
 * @author Pavel Fatin
 */
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

  @NonNls val BuildLibraryName = "sbt-and-plugins"

  @NonNls val UnmanagedLibraryName = "unmanaged-jars"

  @NonNls val UnmanagedSourcesAndDocsName = "unmanaged-sources-and-docs"

  @NonNls val DefinitionHolderClasses = Seq("sbt.Plugin", "sbt.Build")

  // this should be in sync with sbt.BuildUtil.baseImports
  @NonNls val DefaultImplicitImports = Seq("sbt._", "Process._", "Keys._", "dsl._")

  val LatestVersion: Version = Version(BuildInfo.sbtLatestVersion)
  val Latest_1_0: Version = Version(BuildInfo.sbtLatest_1_0)
  val Latest_0_12: Version = Version(BuildInfo.sbtLatest_0_12)
  val Latest_0_13: Version = Version(BuildInfo.sbtLatest_0_13)

  val Icon: Icon = Icons.SBT

  val FolderIcon: Icon = Icons.SBT_FOLDER
}
