package org.jetbrains.plugins.scala
package project.template

import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.vfs.{VfsUtilCore, VirtualFile}

/**
 * @author Pavel Fatin
 */
class ScalaFilesChooserDescriptor extends FileChooserDescriptor(true, true, true, true, false, true) {
  setTitle("Scala SDK files")
  setDescription("Choose either a Scala SDK directory or Scala jar files (allowed: binaries, sources, docs)")

  override def isFileSelectable(file: VirtualFile): Boolean = {
    super.isFileSelectable(file) && file.isDirectory || file.getExtension == "jar"
  }

  override def validateSelectedFiles(virtualFiles: Array[VirtualFile]): Unit = {
    val files = virtualFiles.map(VfsUtilCore.virtualToIoFile)

    val allFiles = files.filter(_.isFile) ++ files.flatMap(_.allFiles)

    val components = Component.discoverIn(allFiles, Artifact.ScalaArtifacts)

    ScalaSdkDescriptor.from(components) match {
      case Left(message) => throw new ValidationException(message)
      case Right(_) => // OK
    }
  }
}
