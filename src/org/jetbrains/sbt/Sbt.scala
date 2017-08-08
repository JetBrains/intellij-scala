package org.jetbrains.sbt

import java.io.File

import com.intellij.openapi.project.Project
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.scala.buildinfo.BuildInfo
import org.jetbrains.plugins.scala.icons.Icons

/**
 * @author Pavel Fatin
 */
object Sbt {
  val Name = "SBT"

  val FileExtension = "sbt"

  val FileDescription = "SBT files"

  val BuildFile = "build.sbt"

  val PropertiesFile = "build.properties"

  val PluginsFile = "plugins.sbt"

  val ProjectDirectory = "project"

  val TargetDirectory = "target"

  val ModulesDirectory = ".idea/modules"

  val ProjectDescription = "SBT project"

  val ProjectLongDescription = "Project backed by SBT"

  val BuildModuleSuffix = "-build"

  val BuildModuleName = "SBT module"

  val BuildModuleDescription = "SBT modules are used to mark content roots and to provide libraries for SBT project definitions"

  val BuildLibraryName = "sbt-and-plugins"

  val UnmanagedLibraryName = "unmanaged-jars"

  val UnmanagedSourcesAndDocsName = "unmanaged-sources-and-docs"

  val DefinitionHolderClasses = Seq("sbt.Plugin", "sbt.Build")

  // this should be in sync with sbt.BuildUtil.baseImports
  val DefaultImplicitImports = Seq("sbt._", "Process._", "Keys._", "dsl._")

  val LatestVersion: String = BuildInfo.sbtLatestVersion

  lazy val Icon = Icons.SBT

  lazy val FileIcon = Icons.SBT_FILE

  def isProjectDefinitionFile(project: Project, file: File): Boolean = {
    val baseDir = new File(project.getBasePath)
    val projectDir = baseDir / Sbt.ProjectDirectory
    file.getName == Sbt.BuildFile && file.isUnder(baseDir) ||
      isSbtFile(file.getName) && file.isUnder(baseDir) ||
      file.getName.endsWith(".scala") && file.isUnder(projectDir)
  }

  def isSbtFile(@NotNull filename: String): Boolean = filename.endsWith(s".${Sbt.FileExtension}")
}
