package org.jetbrains.plugins.scala.lang.autoImport

import com.intellij.openapi.fileTypes.FileType
import org.jetbrains.plugins.scala.worksheet.WorksheetFileType

trait WorksheetFiles extends ScalaFiles {
  override protected def fileType: FileType = WorksheetFileType
}
