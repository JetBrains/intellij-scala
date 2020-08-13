package org.jetbrains.plugins.scala.lang.autoImport

import com.intellij.openapi.fileTypes.FileType
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.worksheet.WorksheetFileType

trait ScalaFiles {
  protected def fileType: FileType = ScalaFileType.INSTANCE
}

trait WorksheetFiles extends ScalaFiles {
  override protected def fileType: FileType = WorksheetFileType
}
