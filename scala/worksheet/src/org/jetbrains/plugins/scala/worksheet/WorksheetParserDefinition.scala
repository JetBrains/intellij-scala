package org.jetbrains.plugins.scala
package worksheet

import com.intellij.psi.FileViewProvider
import com.intellij.psi.tree.IFileElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaParserDefinitionBase
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaFileImpl

final class WorksheetParserDefinition extends ScalaParserDefinitionBase {

  override val getFileNodeType = new IFileElementType("scala.worksheet.file", WorksheetLanguage.INSTANCE)

  override def createFile(viewProvider: FileViewProvider) =
    new WorksheetParserDefinition.WorksheetScalaFile(viewProvider)
}

object WorksheetParserDefinition {

  final class WorksheetScalaFile(viewProvider: FileViewProvider)
    extends ScalaFileImpl(viewProvider, WorksheetFileType) {

    override def isWorksheetFile: Boolean = true
  }
}
