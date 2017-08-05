package org.jetbrains.plugins.cbt.runner

import java.io.File
import java.nio.file._

import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.cbt._

//case class TaskModuleData(module: Module, dir: File)
//
//object TaskModuleData {
//  def apply(dir: String, project: Project): TaskModuleData = {
//    val modules = ModuleManager.getInstance(project).getModules.toSeq.sortBy(_.baseDir.length.unary_-)
//    val fileDir = Paths.get(dir)
//    modules
//      .find(m => fileDir.startsWith(m.getModuleFile.getParent.getCanonicalPath))
//      .map(m => TaskModuleData(m, m.getModuleFile.getParent.getCanonicalPath.toFile))
//      .get
//  }
//}