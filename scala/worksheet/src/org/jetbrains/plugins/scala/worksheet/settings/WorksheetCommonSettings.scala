package org.jetbrains.plugins.scala
package worksheet
package settings

import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.extensions.inReadAction
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerSettingsProfile
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetPerFileConfig

/**
  * User: Dmitry.Naydanov
  * Date: 14.03.18.
  */
abstract class WorksheetCommonSettings extends WorksheetPerFileConfig {
  def project: Project
  
  def getRunType: WorksheetExternalRunType
  
  def isInteractive: Boolean

  def isMakeBeforeRun: Boolean

  def getModuleName: String

  def getCompilerProfileName: String

  def setRunType(runType: WorksheetExternalRunType): Unit

  def setInteractive(value: Boolean): Unit

  def setMakeBeforeRun(value: Boolean): Unit

  def setModuleName(value: String): Unit

  def setCompilerProfileName(value: String): Unit

  def getModuleFor: Module = getModuleName match {
    case null => null
    case moduleName =>
      inReadAction {
        ModuleManager.getInstance(project).findModuleByName(moduleName)
      }
  }

  def getCompilerProfile: ScalaCompilerSettingsProfile
}