package org.jetbrains.bsp.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory

class BspSystemSettingsPane extends BspSystemSettingsForm {

  def setPathListeners(): Unit = {
    bloopExecutablePath.addBrowseFolderListener("Bloop executable", "select bloop executable", null, FileChooserDescriptorFactory.createSingleLocalFileDescriptor)
  }

}
