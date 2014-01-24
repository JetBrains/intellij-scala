package org.jetbrains.jps.incremental.scala
package model

import org.jetbrains.jps.cmdline.ProjectDescriptor
import org.jetbrains.plugin.scala.compiler.{CompileOrder, IncrementalType}

/**
 * Nikolay.Tropin
 * 11/18/13
 */
class ProjectSetingsImpl(projectDescriptor: ProjectDescriptor) extends ProjectSettings {
  private val global = projectDescriptor.getModel.getGlobal

  def incrementalType: IncrementalType = SettingsManager.getGlobalSettings(global).getIncrementalType

  def compileOrder: CompileOrder = SettingsManager.getGlobalSettings(global).getCompileOrder
}
