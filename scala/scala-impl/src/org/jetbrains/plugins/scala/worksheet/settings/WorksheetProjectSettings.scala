package org.jetbrains.plugins.scala.worksheet.settings

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.compiler.ScalaCompileServerSettings
import org.jetbrains.plugins.scala.project.settings.{ScalaCompilerConfiguration, ScalaCompilerSettingsProfile}
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.worksheet.server.{InProcessServer, NonServer, OutOfProcessServer, WorksheetMakeType}

/**
  * User: Dmitry.Naydanov
  * Date: 14.03.18.
  */
class WorksheetProjectSettings(val project: Project) extends WorksheetCommonSettings {
  private val storeComponent = WorksheetDefaultSettings.getInstance(project)
  
  override def isRepl: Boolean = storeComponent.getIsRepl

  override def isInteractive: Boolean = storeComponent.isInteractive

  override def isMakeBeforeRun: Boolean = storeComponent.isMakeBeforeRun

  override def getModuleName: String = storeComponent.getModuleName

  override def getCompilerProfileName: String = storeComponent.getCompilerProfileName

  override def setRepl(value: Boolean): Unit = storeComponent.setRepl(value)

  override def setInteractive(value: Boolean): Unit = storeComponent.setInteractive(value)

  override def setMakeBeforeRun(value: Boolean): Unit = storeComponent.setMakeBeforeRun(value)

  override def setModuleName(value: String): Unit = storeComponent.setModuleName(value)

  override def setCompilerProfileName(value: String): Unit = storeComponent.setCompilerProfileName(value)

  override def getCompilerProfile: ScalaCompilerSettingsProfile = {
    val compilerName = getCompilerProfileName
    val compilerConfiguration = ScalaCompilerConfiguration.instanceIn(project)

    compilerConfiguration.customProfiles.find(_.getName == compilerName).getOrElse(compilerConfiguration.defaultProfile)
  }

  override def getModuleFor: Module = Option(super.getModuleFor).orElse(project.anyScalaModule.map(_.module)).orNull
}

object WorksheetProjectSettings {
  def getRunType(project: Project): WorksheetMakeType = {
    if (ScalaCompileServerSettings.getInstance().COMPILE_SERVER_ENABLED) {
      if (ScalaProjectSettings.getInstance(project).isInProcessMode)
        InProcessServer
      else OutOfProcessServer
    }
    else NonServer
  }
}