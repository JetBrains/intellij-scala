package org.jetbrains.sbt.project.sources

import com.intellij.icons.AllIcons
import com.intellij.openapi.module.ModuleType
import org.jetbrains.sbt.SbtBundle
import org.jetbrains.sbt.project.DummyModuleBuilder

import javax.swing.Icon

class SharedSourcesModuleType extends ModuleType[DummyModuleBuilder]("SHARED_SOURCES_MODULE") {
  override def createModuleBuilder: DummyModuleBuilder = new DummyModuleBuilder()

  override def getName: String = SbtBundle.message("sbt.shared.sources.module")

  override def getDescription: String = SbtBundle.message("sbt.shared.source.module.description")

  override def getNodeIcon(isOpened: Boolean): Icon = AllIcons.Nodes.Package
}

object SharedSourcesModuleType {
  val instance: SharedSourcesModuleType = Class.forName("org.jetbrains.sbt.project.sources.SharedSourcesModuleType").getConstructor().newInstance().asInstanceOf[SharedSourcesModuleType]
}
