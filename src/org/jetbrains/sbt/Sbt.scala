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

  lazy val Icon = IconLoader.getIcon("/sbt.png")

  lazy val FileIcon = IconLoader.getIcon("/sbt-file.png")

  // FIXME: find apropriate place for this function (some `util` module maybe)
  def getProjectBaseByBuildFile(project: Project, buildFilePath: String): Option[String] = {
    import com.intellij.openapi.vfs.VfsUtilCore._
    val changed = new File(buildFilePath)
    val name = changed.getName

    val base = new File(project.getBasePath)
    val build = base / Sbt.ProjectDirectory

    (name == Sbt.BuildFile && isAncestor(base, changed, true) ||
      name.endsWith(s".${Sbt.FileExtension}") && isAncestor(build, changed, true) ||
      name.endsWith(".scala") && isAncestor(build, changed, true))
      .option(base.canonicalPath)
  }
}
