package org.jetbrains.sbt.project.data

import com.intellij.openapi.projectRoots.Sdk
import org.jetbrains.android.sdk.AndroidSdkType
import org.jetbrains.sbt.project.SbtEnvironmentVariablesProvider

private class AndroidSbtEnvironmentVariablesProvider extends SbtEnvironmentVariablesProvider {
  override def getAdditionalVariables(sdk: Sdk): Map[String, String] = sdk.getSdkType match {
    case _: AndroidSdkType => Map("ANDROID_HOME" -> sdk.getSdkModificator.getHomePath)
    case _                 => Map.empty
  }
}
