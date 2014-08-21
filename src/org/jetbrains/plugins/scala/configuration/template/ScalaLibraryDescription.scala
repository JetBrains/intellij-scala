package org.jetbrains.plugins.scala
package configuration.template

import java.util.Collections
import javax.swing.JComponent

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.roots.ui.configuration.libraries.CustomLibraryDescription
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.scala.configuration.ScalaLibraryKind

/**
 * @author Pavel Fatin
 */
object ScalaLibraryDescription extends CustomLibraryDescription {
  def getSuitableLibraryKinds = Collections.singleton(ScalaLibraryKind)

  def createNewLibrary(parentComponent: JComponent, contextDirectory: VirtualFile) = {
    val files = FileChooser.chooseFiles(new ScalaFilesChooserDescriptor(), null, null)

    if (files.length > 0) ScalaSdkDescriptor.from(allFilesWithin(files.toSeq)) match {
      case Left(message) => throw new ValidationException(message)
      case Right(sdk) => sdk.createNewLibraryConfiguration()
    } else {
      null
    }
  }

//  override def getDefaultLevel = LibrariesContainer.LibraryLevel.GLOBAL // TODO
}
