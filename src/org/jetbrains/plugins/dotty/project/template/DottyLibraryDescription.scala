package org.jetbrains.plugins.dotty.project.template

import java.util
import javax.swing.JComponent

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.dotty.project.DottyLibraryKind
import org.jetbrains.plugins.scala.project.template.{ScalaLibraryDescription, SdkChoice}

/**
  * @author adkozlov
  */
object DottyLibraryDescription extends ScalaLibraryDescription {
  override val libraryKind = DottyLibraryKind

  override val sdkDescriptor = DottySdkDescriptor

  override def dialog(parentComponent: JComponent, provider: () => util.List[SdkChoice], contextDirectory: VirtualFile) = {
    new DottySdkSelectionDialog(parentComponent, provider, contextDirectory)
  }
}
