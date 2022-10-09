package org.jetbrains.plugins.scala.testingSupport

import com.intellij.openapi.module.Module
import org.jetbrains.plugins.scala.ExtensionPointDeclaration

abstract class TestWorkingDirectoryProvider {
  def getWorkingDirectory(module: Module): Option[String]
}

object TestWorkingDirectoryProvider extends ExtensionPointDeclaration[TestWorkingDirectoryProvider](
  "org.intellij.scala.testWorkingDirectoryProvider"
)