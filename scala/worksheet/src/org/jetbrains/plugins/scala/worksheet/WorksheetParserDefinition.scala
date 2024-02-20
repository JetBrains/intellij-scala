package org.jetbrains.plugins.scala
package worksheet

import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.tree.IFileElementType
import org.jetbrains.plugins.scala.lang.lexer.ScalaLexer
import org.jetbrains.plugins.scala.lang.parser.{ScalaParser, ScalaParserDefinitionBase}
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScStubFileElementType

/**
 * See also [[org.jetbrains.plugins.scala.worksheet.WorksheetParserDefinition3]]
 */
final class WorksheetParserDefinition extends ScalaParserDefinitionBase {

  override def createLexer(project: Project) =
    new ScalaLexer(/*isScala3 =*/ false, project)

  override def createParser(project: Project): ScalaParser =
    new ScalaParser(isScala3 = false)

  override def getFileNodeType: IFileElementType =
    WorksheetParserDefinition.FileNodeType

  override def createFile(viewProvider: FileViewProvider) =
    new WorksheetFile(viewProvider, WorksheetLanguage.INSTANCE)
}

object WorksheetParserDefinition {

  //noinspection TypeAnnotation
  val FileNodeType = ScStubFileElementType(WorksheetLanguage.INSTANCE)
}
