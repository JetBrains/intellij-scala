package org.jetbrains.sbt

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

  val DefinitionHolderClasses = Seq("sbt.Plugin", "sbt.Build")

  val DefaultImplicitImports = Seq("sbt._", "Process._", "Keys._")

  lazy val Icon = IconLoader.getIcon("/sbt.png")

  lazy val FileIcon = IconLoader.getIcon("/sbt-file.png")
}
