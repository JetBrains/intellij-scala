package org.jetbrains.plugins.scala.worksheet.ui.dialog;

import com.intellij.openapi.module.Module;
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerSettingsProfile;

/**
 * User: Dmitry.Naydanov
 * Date: 07.02.18.
 */
class WorksheetSettingsData {
  final boolean isRepl;
  final boolean isInteractive;
  final boolean isMakeBeforeRun;
  
  final Module moduleName;
  final ScalaCompilerSettingsProfile compilerProfileName;
  
  final ScalaCompilerSettingsProfile[] profiles;

  WorksheetSettingsData(boolean isRepl, boolean isInteractive, boolean isMakeBeforeRun, Module selectedModuleName,
                        ScalaCompilerSettingsProfile selectedCompilerProfileName, ScalaCompilerSettingsProfile[] profiles) {
    this.isRepl = isRepl;
    this.isInteractive = isInteractive;
    this.isMakeBeforeRun = isMakeBeforeRun;
    this.moduleName = selectedModuleName;
    this.compilerProfileName = selectedCompilerProfileName;
    this.profiles = profiles;
  }
}
