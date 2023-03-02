package org.jetbrains.sbt

import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.UsefulTestCase
import org.jetbrains.plugins.scala.base.ScalaSdkOwner
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.sbt.project.module.SbtModuleType

trait MockSbtBuildModule {
  self: UsefulTestCase with ScalaSdkOwner =>

  protected final def setupSbtBuildModule(mainModule: Module): Unit = {
    setupSbtBuildModule(mainModule, None)
  }

  protected final def setupSbtBuildModule(mainModule: Module, jdk: Option[Sdk]): Unit = {
    val sbtBuildModule: Module =
      inWriteAction {
        val buildModuleName = mainModule.getName + Sbt.BuildModuleSuffix //example "root-build"
        val buildModuleFileName = buildModuleName + ".iml"
        val module = ModuleManager.getInstance(mainModule.getProject).newModule(buildModuleFileName, SbtModuleType.instance.getId)
        jdk.foreach(ModuleRootModificationUtil.setModuleSdk(module, _))
        module
      }

    setUpLibraries(sbtBuildModule)
    Disposer.register(getTestRootDisposable, () => {
      disposeLibraries(sbtBuildModule)
    })
  }
}
