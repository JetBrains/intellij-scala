package org.jetbrains.plugins.hydra.compiler

import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project

/**
  * @author Maris Alexandru
  */
object HydraCompilerSettingsManager {
  def showHydraCompileSettingsDialog(project: Project): Unit = ShowSettingsUtil.getInstance().showSettingsDialog(project, "Hydra Compiler")
}
