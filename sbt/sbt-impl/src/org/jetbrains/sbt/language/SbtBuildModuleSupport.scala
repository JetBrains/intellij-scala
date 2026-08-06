package org.jetbrains.sbt.language

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.project.ModuleExt
import org.jetbrains.sbt.Sbt
import org.jetbrains.sbt.project.SbtBuildModuleUriProvider
import org.jetbrains.sbt.project.module.SbtModule.Build

object SbtBuildModuleSupport {
  def findBuildModule(module: Module, project: Project): Option[Module] =
    if (module.hasBuildModuleType)
      Some(module)
    else {
      val manager = ModuleManager.getInstance(project)
      val modules = manager.getModules
      val sbtBuildModuleUri = SbtBuildModuleUriProvider.getBuildModuleUri(module)
      val result = for {
        buildModuleUri <- sbtBuildModuleUri
        module <- modules.find(Build(_) == buildModuleUri)
      } yield module

      if (result.isEmpty && ApplicationManager.getApplication.isUnitTestMode) {
        //NOTE: right now this legacy way of determining build module is left for tests only
        //It simplifies setup logic for tests which work with sbt files.
        //In theory we could remove this extra branch, but we would need to improve setup for tests
        val buildModuleName = module.getName + Sbt.BuildModuleSuffix
        modules.find(_.getName == buildModuleName)
      }
      else result
    }
}
