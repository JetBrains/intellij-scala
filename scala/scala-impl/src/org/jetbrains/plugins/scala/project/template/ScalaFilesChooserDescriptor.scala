package org.jetbrains.plugins.scala
package project.template

import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.vfs.{VfsUtilCore, VirtualFile}
import org.jetbrains.plugins.scala.ScalaBundle

/**
 * @author Pavel Fatin
 */
class ScalaFilesChooserDescriptor extends FileChooserDescriptor(true, true, true, true, false, true) {
  setTitle(ScalaBundle.message("title.scala.sdk.files"))
  setDescription(ScalaBundle.message("choose.either.a.scala.sdk.directory.or.scala.jar.files"))

  override def isFileSelectable(file: VirtualFile): Boolean = {
    super.isFileSelectable(file) && file.isDirectory || file.getExtension == "jar"
  }

  override def validateSelectedFiles(virtualFiles: Array[VirtualFile]): Unit = {
    val files = virtualFiles.map(VfsUtilCore.virtualToIoFile)

    val allFiles = files.filter(_.isFile) ++ files.flatMap(_.allFiles)

    val components = ScalaSdkComponent.discoverIn(allFiles)

    ScalaSdkDescriptor.buildFromComponents(components) match {
      case Left(message) => throw new ValidationException(message)
      case Right(_) => // OK
    }
  }
}
