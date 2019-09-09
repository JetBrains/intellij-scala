package org.jetbrains.plugins.scala.lang.autoImport
import com.intellij.openapi.fileTypes.FileType
import org.jetbrains.plugins.scala.worksheet.WorksheetFileType

trait AutoImportWorksheetTestBase extends AutoImportTestBase {

  override protected def fileType: FileType = WorksheetFileType
}
