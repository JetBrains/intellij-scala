package org.jetbrains.plugins.scala.packagesearch

import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.jetbrains.packagesearch.intellij.plugin.extensibility.{ProjectModule, ProjectModuleProvider}
import kotlin.sequences.Sequence
import org.jetbrains.sbt.{RichFile, Sbt, SbtUtil}

import java.io.File
import scala.jdk.CollectionConverters.IteratorHasAsJava

class SbtProjectModuleProvider extends ProjectModuleProvider{

  def findModulePaths(module: Module): Array[File] = {
    if (!SbtUtil.isSbtModule(module)) return null
    val contentRoots = ModuleRootManager.getInstance(module).getContentRoots
    if (contentRoots.length < 1) return null
    contentRoots.map(virtualFile => {
      new File(virtualFile.getPath)
    })
  }

  def obtainProjectModulesFor(project: Project, module: Module):ProjectModule = {
    var buildFile: File = null
    findModulePaths(module).foreach(file => {
      val tmp = file / Sbt.BuildFile
      if (file.exists()) {
        buildFile = tmp
      }
    })
    if (!buildFile.exists()) return null
    val buildVirtualFile = LocalFileSystem.getInstance().findFileByPath(buildFile.getAbsolutePath)
    if (!buildVirtualFile.exists()) return null

    new ProjectModule(
      module.getName,
      module,
      null,
      buildVirtualFile,
      SbtCommon.buildSystemType,
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
