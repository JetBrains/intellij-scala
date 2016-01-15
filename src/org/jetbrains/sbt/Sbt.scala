package org.jetbrains.sbt

import java.io.File

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader

/**
 * @author Pavel Fatin
 */
object Sbt {
  val Name = "SBT"

  val FileExtension = "sbt"

  val FileDescription = "SBT files"

  val BuildFile = "build.sbt"

  val PluginsFile = "plugins.sbt"

  val PropertiesFile = "build.properties"

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

  val DefaultImplicitImports = Seq("sbt._", "Process._", "Keys._")

  val LatestVersion = "0.13.9"

  lazy val Icon = IconLoader.getIcon("/sbt.png")

  lazy val FileIcon = IconLoader.getIcon("/sbt-file.png")

  def isProjectDefinitionFile(project: Project, file: File): Boolean = {
    val baseDir = new File(project.getBasePath)
    val projectDir = baseDir / Sbt.ProjectDirectory
    file.getName == Sbt.BuildFile && file.isUnder(baseDir) ||
      file.getName.endsWith(s".${Sbt.FileExtension}") && file.isUnder(baseDir) ||
      file.getName.endsWith(".scala") && file.isUnder(projectDir)
  }
}
