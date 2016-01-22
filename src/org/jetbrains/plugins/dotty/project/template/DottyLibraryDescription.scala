package org.jetbrains.plugins.dotty.project.template

import java.util
import javax.swing.JComponent

import org.jetbrains.plugins.dotty.project.DottyLibraryKind
import org.jetbrains.plugins.scala.project.template.{ScalaLibraryDescription, SdkChoice}

/**
  * @author adkozlov
  */
object DottyLibraryDescription extends ScalaLibraryDescription {
  override protected val LibraryKind = DottyLibraryKind

  override protected val SdkDescriptor = DottySdkDescriptor

  override def dialog(parentComponent: JComponent, provider: () => util.List[SdkChoice]) = {
    new DottySdkSelectionDialog(parentComponent, provider)
  }
}
