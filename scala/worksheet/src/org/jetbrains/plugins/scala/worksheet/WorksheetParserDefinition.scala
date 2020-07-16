package org.jetbrains.plugins.scala
package worksheet

import com.intellij.psi.FileViewProvider
import com.intellij.psi.tree.IFileElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaParserDefinitionBase
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScStubFileElementType

final class WorksheetParserDefinition extends ScalaParserDefinitionBase {

  import WorksheetParserDefinition._

  //noinspection TypeAnnotation
  override def getFileNodeType: IFileElementType = FileNodeType

  override def createFile(viewProvider: FileViewProvider) =
    new WorksheetFile(viewProvider)
}

object WorksheetParserDefinition {

  //noinspection TypeAnnotation
  val FileNodeType = ScStubFileElementType(WorksheetLanguage.INSTANCE)
}
