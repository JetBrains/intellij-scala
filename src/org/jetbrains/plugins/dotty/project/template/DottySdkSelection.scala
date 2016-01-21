package org.jetbrains.plugins.dotty.project.template

import javax.swing.JComponent

import org.jetbrains.plugins.scala.project.template.SdkSelection

/**
  * @author adkozlov
  */
object DottySdkSelection extends SdkSelection {
  override protected val SdkDescriptor = DottySdkDescriptor

  override protected def filesChooserDescriptor = new DottyFilesChooserDescriptor

  def chooseDottySdkFiles(parentComponent: JComponent) = chooseSdkFiles(parentComponent)
}
