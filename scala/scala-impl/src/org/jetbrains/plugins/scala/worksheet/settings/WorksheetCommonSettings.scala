package org.jetbrains.plugins.scala.worksheet.settings

import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerSettingsProfile
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetPerFileConfig

/**
  * User: Dmitry.Naydanov
  * Date: 14.03.18.
  */
abstract class WorksheetCommonSettings extends WorksheetPerFileConfig {
  def project: Project
  
  def isRepl: Boolean

  def isInteractive: Boolean

  def isMakeBeforeRun: Boolean

  def getModuleName: String

  def getCompilerProfileName: String

  def setRepl(value: Boolean): Unit

  def setInteractive(value: Boolean): Unit

  def setMakeBeforeRun(value: Boolean): Unit

  def setModuleName(value: String): Unit

  def setCompilerProfileName(value: String): Unit

  def getModuleFor: Module = {
    val moduleName = getModuleName
    
    if (moduleName != null) scala.extensions.inReadAction {
      ModuleManager getInstance project findModuleByName getModuleName
    } else null
  }

  def getCompilerProfile: ScalaCompilerSettingsProfile
}

object WorksheetCommonSettings {
  def getInstance(project: Project): WorksheetCommonSettings = new WorksheetProjectSettings(project)
  def getInstance(file: PsiFile): WorksheetCommonSettings = new WorksheetFileSettings(file)
}