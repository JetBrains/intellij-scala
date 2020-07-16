package org.jetbrains.plugins.scala.codeInsight.intentions

import com.intellij.openapi.fileTypes.FileType
import org.jetbrains.plugins.scala.worksheet.WorksheetFileType

trait ScalaWorksheetIntentionTestBase extends ScalaIntentionTestBase {

  override def fileType: FileType = WorksheetFileType
}
