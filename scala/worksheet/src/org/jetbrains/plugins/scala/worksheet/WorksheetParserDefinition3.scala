package org.jetbrains.plugins.scala.worksheet

import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.tree.IFileElementType
import org.jetbrains.plugins.scala.lang.lexer.ScalaLexer
import org.jetbrains.plugins.scala.lang.parser.{ScalaParser, ScalaParserDefinitionBase}
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScStubFileElementType

/**
 * See also [[org.jetbrains.plugins.scala.worksheet.WorksheetParserDefinition]]
 */
final class WorksheetParserDefinition3 extends ScalaParserDefinitionBase {

  override def createLexer(project: Project) =
    new ScalaLexer(/*isScala3 =*/ true, project)

  override def createParser(project: Project): ScalaParser =
    new ScalaParser(isScala3 = true)
  
  override def getFileNodeType: IFileElementType =
    WorksheetParserDefinition3.FileNodeType

  override def createFile(viewProvider: FileViewProvider) =
    new WorksheetFile(viewProvider, WorksheetLanguage3.INSTANCE)
}

object WorksheetParserDefinition3 {

  //noinspection TypeAnnotation
  val FileNodeType = ScStubFileElementType(WorksheetLanguage3.INSTANCE)
}



