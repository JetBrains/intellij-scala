package org.jetbrains.plugins.scala.util.ui.distribution

import com.intellij.ide.`macro`.Macro
import com.intellij.openapi.fileChooser.{FileChooserDescriptor, FileChooserDescriptorFactory}
import com.intellij.openapi.roots.ui.distribution.FileChooserInfo
import org.jetbrains.annotations.NotNull

import kotlin.jvm.functions.Function1

final class SimpleFileChooserInfo extends FileChooserInfo {
  @NotNull
  override def getFileChooserDescriptor: FileChooserDescriptor =
    FileChooserDescriptorFactory.singleFileOrDir

  override def getFileChooserMacroFilter: Function1[Macro, java.lang.Boolean] =
    /*_*/ FileChooserInfo.Companion.getDIRECTORY_PATH() /*_*/
}
