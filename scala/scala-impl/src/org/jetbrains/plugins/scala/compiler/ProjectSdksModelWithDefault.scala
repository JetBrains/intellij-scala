package org.jetbrains.plugins.scala.compiler

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl
import com.intellij.openapi.projectRoots.{JavaSdk, ProjectJdkTable, Sdk}
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel

private[compiler] class ProjectSdksModelWithDefault extends ProjectSdksModel {
  class DefaultSdk(versionString: String)
    extends ProjectJdkImpl("Project Default", JavaSdk.getInstance(), "", versionString) {

    override def clone(): ProjectJdkImpl = new DefaultSdk(versionString)
  }


  override def reset(project: Project): Unit = {
    super.reset(project)
    val defaultSdk = new DefaultSdk(CompileServerLauncher.defaultSdk(project).getVersionString)
    addSdk(defaultSdk)
  }

  def isDefault(sdk: Sdk): Boolean = sdk.isInstanceOf[DefaultSdk]

  def sdkFrom(settings: ScalaCompileServerSettings): Sdk = {
    if (settings.USE_DEFAULT_SDK) getSdks.find(isDefault).orNull
    else if (settings.COMPILE_SERVER_SDK == null) null
    else ProjectJdkTable.getInstance.findJdk(settings.COMPILE_SERVER_SDK)
  }
}