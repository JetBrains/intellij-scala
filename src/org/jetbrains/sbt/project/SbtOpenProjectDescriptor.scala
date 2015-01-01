package org.jetbrains.sbt
package project

import com.intellij.ide.actions.OpenProjectFileChooserDescriptor
import com.intellij.openapi.vfs.VirtualFile

/**
 * @author Pavel Fatin
 */
class SbtOpenProjectDescriptor extends OpenProjectFileChooserDescriptor(true) {
  override def isFileVisible(file: VirtualFile, showHiddenFiles: Boolean) = {
    super.isFileVisible(file, showHiddenFiles) &&
      (file.isDirectory || file.getName.endsWith("." + Sbt.FileExtension))
  }

  override def isFileSelectable(file: VirtualFile) =
    super.isFileSelectable(file) && SbtProjectImportProvider.canImport(file)
}
