package org.jetbrains.jps.incremental.scala
package model

import org.jetbrains.jps.cmdline.ProjectDescriptor

/**
 * Nikolay.Tropin
 * 11/18/13
 */
class ProjectSetingsImpl(projectDescriptor: ProjectDescriptor) extends ProjectSettings {
  private val global = projectDescriptor.getModel.getGlobal

  def incrementalType: IncrementalType = SettingsManager.getGlobalSettings(global).getIncrementalType

  def compileOrder: Order = SettingsManager.getGlobalSettings(global).getCompileOrder
}
