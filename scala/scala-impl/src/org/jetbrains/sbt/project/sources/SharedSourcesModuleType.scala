package org.jetbrains.sbt.project.sources

import com.intellij.icons.AllIcons
import com.intellij.ide.util.projectWizard.EmptyModuleBuilder
import com.intellij.openapi.module.ModuleType
import javax.swing.Icon
import org.jetbrains.sbt.SbtBundle

/**
 * @author Pavel Fatin
 */
class SharedSourcesModuleType extends ModuleType[EmptyModuleBuilder]("SHARED_SOURCES_MODULE") {
  override def createModuleBuilder() = new EmptyModuleBuilder()

  override def getName: String = SbtBundle.message("sbt.shared.sources.module")

  override def getDescription: String = SbtBundle.message("sbt.shared.source.module.description")

  def getBigIcon: Icon = AllIcons.Nodes.Package

  override def getNodeIcon(isOpened: Boolean): Icon = AllIcons.Nodes.Package
}

object SharedSourcesModuleType {
  val instance: SharedSourcesModuleType = Class.forName("org.jetbrains.sbt.project.sources.SharedSourcesModuleType").getConstructor().newInstance().asInstanceOf[SharedSourcesModuleType]
}
