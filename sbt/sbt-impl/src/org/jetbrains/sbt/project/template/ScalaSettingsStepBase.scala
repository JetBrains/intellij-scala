package org.jetbrains.sbt.project.template

import com.intellij.ide.util.projectWizard.{ModuleBuilder, SdkSettingsStep, SettingsStep}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.projectRoots.{JavaSdk, SimpleJavaSdkType}

class ScalaSettingsStepBase(
  settingsStep: SettingsStep,
  moduleBuilder: ModuleBuilder
) extends SdkSettingsStep(
  settingsStep,
  moduleBuilder,
  /*sdkTypeIdFilter=*/ {
    case _: JavaSdk           => true
    case _: SimpleJavaSdkType => ApplicationManager.getApplication.isUnitTestMode
    case _                    => false
  }
)