package org.jetbrains.plugins.scala.util.ui.distribution

import com.intellij.ide.`macro`.Macro
import com.intellij.openapi.fileChooser.{FileChooserDescriptor, FileChooserDescriptorFactory}
import com.intellij.openapi.roots.ui.distribution.FileChooserInfo
import kotlin.jvm.functions.Function1
import org.jetbrains.annotations.{NotNull, Nullable}

final class SimpleFileChooserInfo extends FileChooserInfo {
  @Nullable
  override def getFileChooserTitle: String = null

  @Nullable
  override def getFileChooserDescription: String = null

  @NotNull
  override def getFileChooserDescriptor: FileChooserDescriptor =
    FileChooserDescriptorFactory.createSingleLocalFileDescriptor

  override def getFileChooserMacroFilter: Function1[Macro, java.lang.Boolean] =
    FileChooserInfo.Companion.getDIRECTORY_PATH()
}