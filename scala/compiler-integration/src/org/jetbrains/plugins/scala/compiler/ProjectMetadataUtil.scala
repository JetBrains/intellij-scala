package org.jetbrains.plugins.scala.compiler

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import org.jetbrains.jps.incremental.scala.ScalaJpsProjectMetadata
import org.jetbrains.plugins.scala.caches.cachedInUserData
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.sbt.project.settings.DisplayModuleName

private object ProjectMetadataUtil {

  /**
   * A cached instance of the JPS project metadata, used for passing precomputed information
   * to the JPS build process about the project to avoid recomputing the data on each build.
   */
  def jpsProjectMetadata(project: Project): ScalaJpsProjectMetadata =
    cachedInUserData("scalaJpsProjectMetadata", project, ProjectRootManager.getInstance(project)) {
      val modulesWithScalaSdk = project.modulesWithScala.map(_.getName).toSet
      val useModuleDisplayName = computeUseModuleDisplayName(project)
      ScalaJpsProjectMetadata(modulesWithScalaSdk, useModuleDisplayName)
    }

  /**
   * Checks whether display module names for all modules are unique.
   * If yes, then display module names will be used in compilation charts.
   * Otherwise, full module names will be used for all modules.
   *
   * The case in which display names may be duplicated is when there is a multi build sbt project,
   * or there are many separate sbt projects imported in IDEA.
   * Then it may happen that in two builds there will be sbt projects with the same names, and
   * these names are used as display names. Therefore, duplication occurs. <br>
   * This problem is not present when creating modules, as then we add the root project prefix, which is unique.
   */
  def computeUseModuleDisplayName(project: Project): Boolean = {
    val modules = ModuleManager.getInstance(project).getModules
    val displayModuleNames = modules.map(DisplayModuleName.getInstance(_).name)
    val containsNull = displayModuleNames.contains(null)
    val isUnique = displayModuleNames.toSet.size == displayModuleNames.length
    !containsNull && isUnique
  }
}
