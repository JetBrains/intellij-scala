package org.jetbrains.plugins.scala
package configuration.template

import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.vfs.{VfsUtilCore, VirtualFile}

/**
 * @author Pavel Fatin
 */
class ScalaFilesChooserDescriptor extends FileChooserDescriptor(true, true, true, true, false, true) {
  setTitle("Scala SDK")
  setDescription("Choose either a Scala SDK directory or Scala jar files (allowed: binaries, sources, docs)")

  override def isFileSelectable(file: VirtualFile) = {
    super.isFileSelectable(file) && file.isDirectory || file.getExtension == "jar"
  }

  override def validateSelectedFiles(virtualFiles: Array[VirtualFile]) = {
    val files = virtualFiles.map(VfsUtilCore.virtualToIoFile)

    val allFiles = files.filter(_.isFile) ++ files.flatMap(_.allFiles)

    val components = Component.discoverIn(allFiles)

    ScalaSdkDescriptor.from(components) match {
      case Left(message) => throw new ValidationException(message)
      case Right(sdk) => // OK
    }
  }
}
