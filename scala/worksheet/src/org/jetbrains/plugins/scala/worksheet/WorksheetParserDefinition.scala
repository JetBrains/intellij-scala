package org.jetbrains.plugins.scala
package worksheet

import com.intellij.psi.FileViewProvider
import org.jetbrains.plugins.scala.lang.psi._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaFileImpl

//noinspection TypeAnnotation
final class WorksheetParserDefinition extends lang.parser.ScalaParserDefinitionBase(
  new stubs.elements.ScStubFileElementType("scala.worksheet.file", WorksheetLanguage.INSTANCE)
) {

  override def createFile(viewProvider: FileViewProvider): ScalaFileImpl = new WorksheetScalaFile(viewProvider)
}

final class WorksheetScalaFile(viewProvider: FileViewProvider)
  extends ScalaFileImpl(viewProvider, WorksheetFileType) {

  override def isWorksheetFile: Boolean = true
}
