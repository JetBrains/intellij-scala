package org.jetbrains.plugins.scala.packageSearch

import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.jetbrains.packagesearch.intellij.plugin.extensibility.{ProjectModule, ProjectModuleProvider}
import kotlin.sequences.Sequence
import org.jetbrains.sbt.{RichFile, Sbt, SbtUtil}

import java.io.File
import scala.jdk.CollectionConverters.IteratorHasAsJava

class SbtProjectModuleProvider extends ProjectModuleProvider{

  def findExternalProjectPath(project: Project, module: Module): File = {
    if (!SbtUtil.isSbtModule(module)) return null
    val rootProjectPath = ExternalSystemApiUtil.getExternalRootProjectPath(module)
    if (rootProjectPath == null) return null
    new File(rootProjectPath)
  }

  def obtainProjectModulesFor(project: Project, module: Module):ProjectModule = {
    val buildFile = (findExternalProjectPath(project, module) / Sbt.BuildFile)
    if (!buildFile.exists()) {
      return null
    }
    val buildVirtualFile = LocalFileSystem.getInstance().findFileByPath(buildFile.getAbsolutePath)
    if (!buildVirtualFile.exists()) return null

    new ProjectModule(
      module.getName,
      module,
      null,
      buildVirtualFile,
      SbtObjects.buildSystemType,
      SbtProjectModuleType
    )
  }

  override def obtainAllProjectModulesFor(project: Project): Sequence[ProjectModule] = {
    val projectModules = ModuleManager.getInstance(project).getModules
      .map(module => obtainProjectModulesFor(project, module))
      .filter(_ != null)
      .distinct
    val res = ScalaHelper.toKotlinSequence(projectModules.iterator.asJava)
    res
  }
}
