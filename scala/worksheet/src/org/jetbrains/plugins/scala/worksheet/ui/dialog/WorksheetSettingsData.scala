package org.jetbrains.plugins.scala.worksheet.ui.dialog

import com.intellij.openapi.module.Module
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerSettingsProfile
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetExternalRunType

case class WorksheetSettingsData(
  isInteractive: Boolean,
  isMakeBeforeRun: Boolean,
  runType: WorksheetExternalRunType,
  cpModule: Module,
  compilerProfile: ScalaCompilerSettingsProfile,
  profiles: Array[ScalaCompilerSettingsProfile]
)