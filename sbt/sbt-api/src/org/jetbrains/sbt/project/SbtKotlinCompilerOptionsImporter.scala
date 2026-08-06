package org.jetbrains.sbt.project

import com.intellij.openapi.module.Module
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import org.jetbrains.annotations.TestOnly

import java.util

trait SbtKotlinCompilerOptionsImporter {

  def setAdditionalArguments(
    module: Module,
    options: util.List[String],
    modelsProvider: IdeModifiableModelsProvider
  ): Unit

  @TestOnly
  def getAdditionalArguments(module: Module): util.List[String]
}
