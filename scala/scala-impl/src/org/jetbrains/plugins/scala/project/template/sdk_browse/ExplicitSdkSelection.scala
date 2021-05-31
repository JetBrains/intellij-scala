package org.jetbrains.plugins.scala.project.template.sdk_browse

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VfsUtilCore
import org.jetbrains.plugins.scala.NlsString
import org.jetbrains.plugins.scala.project.template.{FileExt, ScalaSdkComponent, ScalaSdkDescriptor}

import javax.swing.JComponent

private[template] object ExplicitSdkSelection {

  def chooseScalaSdkFiles(parentComponent: JComponent): Option[ScalaSdkDescriptor] = {
    ExplicitSdkSelection.browse(parentComponent).flatMap {
      case Left(message) =>
        Messages.showErrorDialog(parentComponent, message.nls)
        None
      case Right(sdk) =>
        Some(sdk)
    }
  }

  private def browse(parent: JComponent): Option[Either[NlsString, ScalaSdkDescriptor]] = {
    val virtualFiles = FileChooser.chooseFiles(new ScalaFilesChooserDescriptor(), parent, null, null).toSeq

    val files = virtualFiles.map(VfsUtilCore.virtualToIoFile)

    val allFiles = files.filter(_.isFile) ++ files.flatMap(_.allFiles)

    val components = ScalaSdkComponent.fromFiles(allFiles)

    if (files.nonEmpty) Some(ScalaSdkDescriptor.buildFromComponents(components))
    else None
  }
}
