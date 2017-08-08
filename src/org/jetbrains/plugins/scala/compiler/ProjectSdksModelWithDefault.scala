package org.jetbrains.plugins.scala.compiler

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.{JavaSdk, ProjectJdkTable, Sdk, SdkTypeId}
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel

/**
  * Nikolay.Tropin
  * 13-Jul-17
  */
private[compiler] class ProjectSdksModelWithDefault extends ProjectSdksModel {
  object DefaultSdk extends ProjectJdkImpl("Project Default", JavaSdk.getInstance()) {
    override def clone(): ProjectJdkImpl = DefaultSdk
  }

  override def reset(project: Project): Unit = {
    super.reset(project)
    addSdk(DefaultSdk)
  }

  def isDefault(sdk: Sdk): Boolean = sdk == DefaultSdk

  def sdkFrom(settings: ScalaCompileServerSettings): Sdk = {
    if (settings.USE_DEFAULT_SDK) DefaultSdk
    else if (settings.COMPILE_SERVER_SDK == null) null
    else ProjectJdkTable.getInstance.findJdk(settings.COMPILE_SERVER_SDK)
  }
}