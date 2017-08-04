package org.jetbrains.plugins.cbt

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.sbt.RichVirtualFile


object CBT {
  val Icon = Icons.CBT

  def isCbtModuleDir(entry: VirtualFile): Boolean =
    entry.containsDirectory("build")

  val cbtBuildClassNames: Seq[String] =
    Seq("BaseBuild", "BasicBuild", "BuildBuild", "Plugin")
}
