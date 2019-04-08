package org.jetbrains.plugins.scala
package worksheet

import com.intellij.psi.FileViewProvider
import org.jetbrains.plugins.scala.lang.psi._

//noinspection TypeAnnotation
final class WorksheetParserDefinition extends lang.parser.ScalaParserDefinitionBase(
  new stubs.elements.ScStubFileElementType("scala.worksheet.file", WorksheetLanguage.INSTANCE)
) {

  override def createFile(viewProvider: FileViewProvider) =
    new impl.ScalaFileImpl(viewProvider, WorksheetFileType) {
      override def isWorksheetFile: Boolean = true
    }
}