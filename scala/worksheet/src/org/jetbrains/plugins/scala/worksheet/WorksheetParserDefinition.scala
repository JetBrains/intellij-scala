package org.jetbrains.plugins.scala
package worksheet

import com.intellij.psi.FileViewProvider
import org.jetbrains.plugins.scala.lang.parser.ScalaParserDefinitionBase
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaFileImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScStubFileElementType

final class WorksheetParserDefinition extends ScalaParserDefinitionBase {

  import WorksheetParserDefinition._

  //noinspection TypeAnnotation
  override def getFileNodeType = FileNodeType

  override def createFile(viewProvider: FileViewProvider) =
    new WorksheetScalaFile(viewProvider)
}

object WorksheetParserDefinition {

  private val FileNodeType = ScStubFileElementType(WorksheetLanguage.INSTANCE)

  final class WorksheetScalaFile(viewProvider: FileViewProvider)
    extends ScalaFileImpl(viewProvider, WorksheetFileType) {

    override def isWorksheetFile: Boolean = true
  }
}
