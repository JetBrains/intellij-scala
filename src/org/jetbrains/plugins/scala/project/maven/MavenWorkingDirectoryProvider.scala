package org.jetbrains.plugins.scala.project.maven

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module

/**
 * @author Roman.Shein
 * @since 30.10.2015.
 */
abstract class MavenWorkingDirectoryProvider {
  def getWorkingDirectory(module: Module): String
}

object MavenWorkingDirectoryProvider {
  val EP_NAME: ExtensionPointName[MavenWorkingDirectoryProvider] = ExtensionPointName.create("org.intellij.scala.mavenWorkingDirectoryProvider")
}