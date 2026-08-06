package org.jetbrains.plugins.scala.worksheet.settings

import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.project.{ModuleExt, ProjectExt}
import org.jetbrains.sbt.SbtSourceSetUtil.SbtSourceSetModuleExt
import org.jetbrains.sbt.SbtUtil

private object WorksheetModuleUtil {
  def allModulesWithScalaSdk(project: Project): Seq[Module] = {
    val allScalaModules = project.modulesWithScala
    val modulesWithScalaLibrary = allScalaModules.filter { module =>
      val libraries = module.libraries
      libraries.exists { library =>
        val name = library.getName
        (name ne null) && (name.contains("scala-library") || name.contains("scala3-library"))
      }
    }
    // In case there are no modules left, just use the non-filtered list. Some tests also start to fail in this case.
    if (modulesWithScalaLibrary.nonEmpty) modulesWithScalaLibrary else allScalaModules
  }

  def allProductionModulesWithScalaSdk(project: Project): Seq[Module] = {
    val separate = SbtUtil.isBuiltWithSeparateModulesForProdTest(project)
    val modules = allModulesWithScalaSdk(project)
    // If the project has separate production and test modules enabled, only offer the production modules as a choice.
    if (separate) modules.filter(_.isMain) else modules
  }

  def isStale(module: Module): Boolean = {
    val externalProjectPath = Option(ExternalSystemApiUtil.getExternalRootProjectPath(module))
    val separate = SbtUtil.isBuiltWithSeparateModulesForProdTest(module.getProject, externalProjectPath)
    val isMainOrTest = module.isMain || module.isTest
    if (separate) !isMainOrTest else isMainOrTest
  }
}
