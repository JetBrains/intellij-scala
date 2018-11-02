package org.jetbrains.plugins.hydra.compiler

import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project

/**
  * @author Maris Alexandru
  */
object HydraCompilerSettingsManager {

  private val HydraLogKey = "hydra.logFile"

  def showHydraCompileSettingsDialog(project: Project): Unit = ShowSettingsUtil.getInstance().showSettingsDialog(project, "Hydra Compiler")

  def getHydraLogJvmParameter(project: Project): Option[String] = {
    val settings = HydraCompilerSettings.getInstance(project)
    if (settings.isHydraEnabled)
      Some(s"-Dhydra.logFile=${settings.hydraLogLocation}")
    else
      None
  }

  def setHydraLogSystemProperty(project: Project): Unit = {
    if (System.getProperty(HydraLogKey) == null) {
      val settings = HydraCompilerSettings.getInstance(project)
      System.setProperty(HydraLogKey, settings.hydraLogLocation)
    }
  }
}
