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

  val ProjectDirectory = "project"

  val ProjectDescription = "SBT project"

  val BuildModuleSuffix = "-build"

  val BuildModuleName = "SBT module"

  val BuildModuleDescription = "Create empty SBT project"

  val BuildLibraryName = "sbt-and-plugins"

  val UnmanagedLibraryName = "sbt-unmanaged-jars"

  lazy val Icon = IconLoader.getIcon("/sbt.png")

  lazy val FileIcon = IconLoader.getIcon("/sbt-file.png")
}
