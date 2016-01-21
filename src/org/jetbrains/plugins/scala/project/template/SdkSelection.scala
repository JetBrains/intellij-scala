package org.jetbrains.plugins.scala
package project.template

import javax.swing.JComponent

import com.intellij.openapi.fileChooser.{FileChooser, FileChooserDescriptor}
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VfsUtilCore

/**
 * @author Pavel Fatin
 */
object SdkSelection extends SdkSelection {
  override protected val SdkDescriptor = ScalaSdkDescriptor

  override protected def filesChooserDescriptor = new ScalaFilesChooserDescriptor

  def chooseScalaSdkFiles(parentComponent: JComponent) = chooseSdkFiles(parentComponent)
}

trait SdkSelection {
  protected val SdkDescriptor: SdkDescriptorCompanion

  protected def filesChooserDescriptor: FileChooserDescriptor

  protected def chooseSdkFiles(parentComponent: JComponent): Option[SdkDescriptor] = {
    browse(parentComponent).flatMap {
      case Left(message) =>
        Messages.showErrorDialog(parentComponent, message)
        None
      case Right(sdk) => Some(sdk)
    }
  }

  protected def browse(parent: JComponent): Option[Either[String, SdkDescriptor]] = {
    val virtualFiles = FileChooser.chooseFiles(filesChooserDescriptor, parent, null, null).toSeq

    val files = virtualFiles.map(VfsUtilCore.virtualToIoFile)

    val allFiles = files.filter(_.isFile) ++ files.flatMap(_.allFiles)

    val components = Component.discoverIn(allFiles)

    if (files.nonEmpty) Some(SdkDescriptor.from(components)) else None
  }
}
