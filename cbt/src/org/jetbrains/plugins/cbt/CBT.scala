package org.jetbrains.plugins.cbt

import java.nio.file.Paths
import javax.swing.Icon

import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.sbt.RichVirtualFile

import scala.util.Try


object CBT {
  val Icon: Icon = Icons.CBT

  def isCbtModuleDir(entry: VirtualFile): Boolean =
    entry.containsDirectory("build")

  def isCbtBuildFile(entry: VirtualFile): Boolean =
    entry.isFile && entry.getName == "build.scala"

  val cbtBuildClassNames: Seq[String] =
    Seq("BaseBuild", "BasicBuild", "BuildBuild", "Plugin")

  def moduleByPath(dir: String, project: Project): Option[Module] = {
    val fileDir = Paths.get(dir)
    ModuleManager.getInstance(project).getModules
      .toSeq
      .sortBy(_.baseDir.length.unary_-)
      .find { m =>
        Try(fileDir.startsWith(m.getModuleFile.getParent.getCanonicalPath)).toOption.contains(true)
      }
  }
}
