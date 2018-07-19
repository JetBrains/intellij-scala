package org.jetbrains.plugins.scala.worksheet.ui.dialog;

import com.intellij.openapi.module.Module;
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerSettingsProfile;
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetExternalRunType;

/**
 * User: Dmitry.Naydanov
 * Date: 07.02.18.
 */
class WorksheetSettingsData {
  final boolean isInteractive;
  final boolean isMakeBeforeRun;
  final WorksheetExternalRunType runType;
  
  final Module cpModule;
  final ScalaCompilerSettingsProfile compilerProfile;
  
  final ScalaCompilerSettingsProfile[] profiles;

  WorksheetSettingsData(boolean isInteractive, boolean isMakeBeforeRun, WorksheetExternalRunType runType, Module selectedCpModule,
                        ScalaCompilerSettingsProfile selectedCompilerProfile, ScalaCompilerSettingsProfile[] profiles) {
    this.isInteractive = isInteractive;
    this.isMakeBeforeRun = isMakeBeforeRun;
    this.runType = runType;
    this.cpModule = selectedCpModule;
    this.compilerProfile = selectedCompilerProfile;
    this.profiles = profiles;
  }
}
