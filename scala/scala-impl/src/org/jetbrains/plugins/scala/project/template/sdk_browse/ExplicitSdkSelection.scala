package org.jetbrains.plugins.scala.project.template.sdk_browse

import com.intellij.openapi.fileChooser.FileChooser
import org.jetbrains.plugins.scala.project.template.ScalaSdkDescriptor

import javax.swing.JComponent

private[template] object ExplicitSdkSelection {

  def chooseScalaSdkFiles(parentComponent: JComponent): Option[ScalaSdkDescriptor] = {
    val chooser = new ScalaSdkFilesChooserDescriptor()
    val virtualFiles = FileChooser.chooseFiles(chooser, parentComponent, null, null).toSeq
    val selectedSomeFiles = virtualFiles.nonEmpty
    if (selectedSomeFiles)
      chooser.resultSdkDescriptor
    else None
  }
}
