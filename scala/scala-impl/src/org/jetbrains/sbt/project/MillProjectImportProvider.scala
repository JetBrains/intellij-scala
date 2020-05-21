package org.jetbrains.sbt.project

import com.intellij.openapi.vfs.VirtualFile
import java.io.File
import scala.io.Source

object MillProjectImportProvider {
  private val buildFile = "build.sc"

  def canImport(vFile: VirtualFile): Boolean = vFile match {
    case null => false
    case directory if directory.isDirectory =>
      val buildWithBspPlugin = directory.getChildren.exists { vBuildScript =>
        vBuildScript.getName == "build.sc" &&
          Source.fromInputStream(vBuildScript.getInputStream)
            .getLines()
            .contains("import $ivy.`com.lihaoyi::mill-contrib-bsp:$MILL_VERSION`")
      }
      val bootstrap = directory.getChildren.exists { child =>
        val file = new File(child.getPath)
        file.getName == "mill" && file.canExecute
      }

      buildWithBspPlugin && bootstrap
    case file => file.getName == buildFile
  }
}
