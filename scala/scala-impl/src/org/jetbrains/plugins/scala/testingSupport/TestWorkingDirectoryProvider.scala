package org.jetbrains.plugins.scala.testingSupport

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module

/**
 * @author Roman.Shein
 * @since 30.10.2015.
 */
abstract class TestWorkingDirectoryProvider {
  def getWorkingDirectory(module: Module): String
}

object TestWorkingDirectoryProvider {
  val EP_NAME: ExtensionPointName[TestWorkingDirectoryProvider] =
    ExtensionPointName.create("org.intellij.scala.testWorkingDirectoryProvider")
}