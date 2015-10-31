package org.jetbrains.plugins.scala.project.maven

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module

/**
 * @author Roman.Shein
 * @since 30.10.2015.
 */
abstract class ScalaTestDefaultWorkingDirectoryProvider {
  def getWorkingDirectory(module: Module): String
}

object ScalaTestDefaultWorkingDirectoryProvider {
  val EP_NAME: ExtensionPointName[ScalaTestDefaultWorkingDirectoryProvider] =
    ExtensionPointName.create("org.intellij.scala.scalaTestDefaultWorkingDirectoryProvider")
}