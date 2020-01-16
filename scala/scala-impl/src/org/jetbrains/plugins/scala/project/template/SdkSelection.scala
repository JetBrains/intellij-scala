package org.jetbrains.plugins.scala
package project.template

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VfsUtilCore
import javax.swing.JComponent

/**
 * @author Pavel Fatin
 */
object SdkSelection {
  def chooseScalaSdkFiles(parentComponent: JComponent): Option[ScalaSdkDescriptor] = {
    SdkSelection.browse(parentComponent).flatMap {
      case Left(message) =>
        Messages.showErrorDialog(parentComponent, message)
        None
      case Right(sdk) => Some(sdk)
    }
  }

  def browse(parent: JComponent): Option[Either[String, ScalaSdkDescriptor]] = {
    val virtualFiles = FileChooser.chooseFiles(new ScalaFilesChooserDescriptor(), parent, null, null).toSeq

    val files = virtualFiles.map(VfsUtilCore.virtualToIoFile)

    val allFiles = files.filter(_.isFile) ++ files.flatMap(_.allFiles)

    val components = ScalaSdkComponent.discoverIn(allFiles)

    if (files.nonEmpty) Some(ScalaSdkDescriptor.buildFromComponents(components)) else None
  }
}
