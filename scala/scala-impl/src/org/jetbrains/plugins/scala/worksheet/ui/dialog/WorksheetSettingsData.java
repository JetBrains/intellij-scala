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
  
  final Module cpModule;
  final ScalaCompilerSettingsProfile compilerProfile;
  
  final ScalaCompilerSettingsProfile[] profiles;

  WorksheetSettingsData(boolean isRepl, boolean isInteractive, boolean isMakeBeforeRun, Module selectedCpModule,
                        ScalaCompilerSettingsProfile selectedCompilerProfile, ScalaCompilerSettingsProfile[] profiles) {
    this.isRepl = isRepl;
    this.isInteractive = isInteractive;
    this.isMakeBeforeRun = isMakeBeforeRun;
    this.cpModule = selectedCpModule;
    this.compilerProfile = selectedCompilerProfile;
    this.profiles = profiles;
  }
}
