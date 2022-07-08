package org.jetbrains.plugins.scala
package editor

import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

import java.io.File

class PackageObjectEditorTabTitleProvider extends EditorTabTitleProvider {
  override def getEditorTabTitle(project: Project, file: VirtualFile): String = {
    file.getName match {
      case "package.scala" if file.getParent != null =>
        val dirName = file.getParent.getName
        s"$dirName${File.separator}package.scala"
      case _ => null
    }
  }
}
