package org.jetbrains.plugins.cbt

import java.nio.file.Paths

import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.sbt.RichVirtualFile


object CBT {
  val Icon = Icons.CBT

  def isCbtModuleDir(entry: VirtualFile): Boolean =
    entry.containsDirectory("build")

  val cbtBuildClassNames: Seq[String] =
    Seq("BaseBuild", "BasicBuild", "BuildBuild", "Plugin")

  def moduleByPath(dir: String, project: Project): Module = {
    val modules = ModuleManager.getInstance(project).getModules.toSeq.sortBy(_.baseDir.length.unary_-)
    val fileDir = Paths.get(dir)
    modules
      .find(m => fileDir.startsWith(m.getModuleFile.getParent.getCanonicalPath))
      .get
  }
}
