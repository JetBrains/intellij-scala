package org.jetbrains.sbt.project.sources

import com.intellij.icons.AllIcons
import com.intellij.ide.util.projectWizard.EmptyModuleBuilder
import com.intellij.openapi.module.ModuleType

/**
 * @author Pavel Fatin
 */
class SharedSourcesModuleType extends ModuleType[EmptyModuleBuilder]("SHARED_SOURCES_MODULE") {
  def createModuleBuilder() = new EmptyModuleBuilder()

  def getName = "Shared sources module"

  def getDescription = "During compilation, dependency to a shared sources module mixes in module sources rather than module output"

  def getBigIcon = AllIcons.Modules.SourceFolder

  override def getNodeIcon(isOpened: Boolean) = AllIcons.Modules.SourceFolder
}

object SharedSourcesModuleType {
  val instance = Class.forName("org.jetbrains.sbt.project.sources.SharedSourcesModuleType").newInstance.asInstanceOf[SharedSourcesModuleType]
}
