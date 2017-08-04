package org.jetbrains.plugins.cbt.runner

import java.nio.file._

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.cbt._

case class TaskModuleData(name: String, dir: String)

object TaskModuleData {
  def apply(dir: String, project: Project): TaskModuleData = {
    val modules = ModuleManager.getInstance(project).getModules.toSeq.sortBy(_.baseDir.length.unary_-)
    val fileDir = Paths.get(dir)
    modules
      .find(m => fileDir.startsWith(m.getModuleFile.getParent.getCanonicalPath))
      .map(m => TaskModuleData(m.getName, m.getModuleFile.getParent.getCanonicalPath))
      .get
  }
}