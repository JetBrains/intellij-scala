package org.jetbrains.plugins.scala.lang.autoImport

import com.intellij.openapi.fileTypes.FileType
import org.jetbrains.plugins.scala.ScalaFileType

trait ScalaFiles {
  protected def fileType: FileType = ScalaFileType.INSTANCE
}


